/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.planner.operators;

import io.crate.analyze.OrderBy;
import io.crate.analyze.relations.QueriedRelation;
import io.crate.data.Row;
import io.crate.execution.dsl.projection.builder.ProjectionBuilder;
import io.crate.expression.symbol.Field;
import io.crate.expression.symbol.RefVisitor;
import io.crate.expression.symbol.SelectSymbol;
import io.crate.expression.symbol.Symbol;
import io.crate.planner.ExecutionPlan;
import io.crate.planner.PlannerContext;
import io.crate.planner.SubqueryPlanner;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An Operator that marks the boundary of a relation.
 * In relational algebra terms this is a "no-op" operator - it doesn't apply any modifications on the source relation.
 *
 * It is used to take care of the field mapping (providing {@link LogicalPlan#expressionMapping()})
 * In addition it takes care of MultiPhase planning.
 */
public class RelationBoundary extends OneInputPlan {

    public static LogicalPlan.Builder create(LogicalPlan.Builder sourceBuilder,
                                             QueriedRelation relation,
                                             SubqueryPlanner subqueryPlanner) {
        return (tableStats, usedBeforeNextFetch) -> {
            HashMap<Symbol, Symbol> expressionMapping = new HashMap<>();
            HashMap<Symbol, Symbol> reverseMapping = new HashMap<>();
            for (Field field : relation.fields()) {
                Symbol value = ((QueriedRelation) field.relation()).outputs().get(field.index());
                expressionMapping.put(field, value);
                reverseMapping.put(value, field);
            }
            Function<Symbol, Symbol> mapper = OperatorUtils.getMapper(expressionMapping);
            HashSet<Symbol> mappedUsedColumns = new LinkedHashSet<>();
            for (Symbol beforeNextFetch : usedBeforeNextFetch) {
                mappedUsedColumns.add(mapper.apply(beforeNextFetch));
            }
            LogicalPlan source = sourceBuilder.build(tableStats, mappedUsedColumns);
            for (Symbol symbol : source.outputs()) {
                RefVisitor.visitRefs(symbol, r -> {
                    Field field = new Field(relation, r.column(), r.valueType());
                    if (reverseMapping.putIfAbsent(r, field) == null) {
                        expressionMapping.put(field, r);
                    }
                });
            }
            List<Symbol> outputs = OperatorUtils.mappedSymbols(source.outputs(), reverseMapping);
            expressionMapping.putAll(source.expressionMapping());
            Map<LogicalPlan, SelectSymbol> subQueries = subqueryPlanner.planSubQueries(relation);
            return new RelationBoundary(source, relation, outputs, expressionMapping, reverseMapping, subQueries);
        };
    }

    private final QueriedRelation relation;
    private final Map<Symbol, Symbol> reverseMapping;

    private RelationBoundary(LogicalPlan source,
                             QueriedRelation relation,
                             List<Symbol> outputs,
                             Map<Symbol, Symbol> expressionMapping,
                             Map<Symbol, Symbol> reverseMapping,
                             Map<LogicalPlan, SelectSymbol> subQueries) {
        super(source, outputs, expressionMapping, source.baseTables(), subQueries);
        subQueries.putAll(source.dependencies());
        this.relation = relation;
        this.reverseMapping = reverseMapping;
    }

    @Override
    public LogicalPlan tryOptimize(@Nullable LogicalPlan ancestor, SymbolMapper mapper) {
        if (ancestor instanceof Order) {
            LogicalPlan newSource = source.tryOptimize(ancestor, mapper.andThen(outputs, SymbolMapper.fromMap(expressionMapping)));
            if (newSource != null && newSource != source) {
                return updateSource(newSource, mapper);
            }
        }
        if (ancestor instanceof Filter) {
            LogicalPlan newSource = source.tryOptimize(ancestor, mapper.andThen(outputs, SymbolMapper.fromMap(expressionMapping)));
            if (newSource != null && newSource != source) {
                return updateSource(newSource, mapper);
            }
        }
        return super.tryOptimize(ancestor, mapper);
    }

    @Override
    public ExecutionPlan build(PlannerContext plannerContext,
                               ProjectionBuilder projectionBuilder,
                               int limit,
                               int offset,
                               @Nullable OrderBy order,
                               @Nullable Integer pageSizeHint,
                               Row params,
                               SubQueryResults subQueryResults) {
        return source.build(
            plannerContext, projectionBuilder, limit, offset, order, pageSizeHint, params, subQueryResults);
    }

    @Override
    public long numExpectedRows() {
        return source.numExpectedRows();
    }

    @Override
    public String toString() {
        return "Boundary{" + source + '}';
    }

    @Override
    protected LogicalPlan updateSource(LogicalPlan newSource, SymbolMapper mapper) {
        return new RelationBoundary(
            newSource,
            relation,
            OperatorUtils.mappedSymbols(newSource.outputs(), reverseMapping),
            expressionMapping,
            reverseMapping,
            dependencies);
    }

    @Override
    public <C, R> R accept(LogicalPlanVisitor<C, R> visitor, C context) {
        return visitor.visitRelationBoundary(this, context);
    }
}

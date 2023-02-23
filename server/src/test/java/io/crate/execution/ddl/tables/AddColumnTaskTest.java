/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.execution.ddl.tables;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterState;
import org.junit.Test;

import com.carrotsearch.hppc.IntArrayList;

import io.crate.metadata.Reference;
import io.crate.metadata.ReferenceIdent;
import io.crate.metadata.RowGranularity;
import io.crate.metadata.SimpleReference;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.metadata.doc.DocTableInfoFactory;
import io.crate.test.integration.CrateDummyClusterServiceUnitTest;
import io.crate.testing.IndexEnv;
import io.crate.testing.SQLExecutor;
import io.crate.types.DataTypes;

public class AddColumnTaskTest extends CrateDummyClusterServiceUnitTest {

    @Test
    public void test_can_add_child_column() throws Exception {
        var e = SQLExecutor.builder(clusterService)
            .addTable("create table tbl (x int, o object)")
            .build();
        DocTableInfo tbl = e.resolveTableInfo("tbl");
        try (IndexEnv indexEnv = new IndexEnv(
            THREAD_POOL,
            tbl,
            clusterService.state(),
            Version.CURRENT,
            createTempDir()
        )) {
            var addColumnTask = new AddColumnTask(e.nodeCtx, imd -> indexEnv.mapperService());
            ReferenceIdent refIdent = new ReferenceIdent(tbl.ident(), "o", List.of("x"));
            SimpleReference newColumn = new SimpleReference(
                refIdent,
                RowGranularity.DOC,
                DataTypes.INTEGER,
                3,
                null
            );
            List<Reference> columns = List.of(newColumn);
            var request = new AddColumnRequest(
                tbl.ident(),
                columns,
                Map.of(),
                new IntArrayList()
            );
            ClusterState newState = addColumnTask.execute(clusterService.state(), request);
            DocTableInfo newTable = new DocTableInfoFactory(e.nodeCtx).create(tbl.ident(), newState);

            Reference addedColumn = newTable.getReference(newColumn.column());
            assertThat(addedColumn).isEqualTo(newColumn);
        }
    }
}
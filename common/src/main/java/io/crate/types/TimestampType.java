/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.types;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public final class TimestampType extends BaseTimestampType {

    public static final TimestampType INSTANCE = new TimestampType();
    public static final int ID = 15;

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .append(ISO_LOCAL_DATE_TIME)
        .optionalStart().appendPattern("[VV][x][xx][xxx]")
        .toFormatter(Locale.ENGLISH);

    private TimestampType() {
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public String getName() {
        return "timestamp";
    }

    @Override
    public Long valueFrom(String timestamp) {
        LocalDateTime dt = LocalDateTime.parse(timestamp, FORMATTER);
        return dt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}

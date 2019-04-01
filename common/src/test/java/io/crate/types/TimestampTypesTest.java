package io.crate.types;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class TimestampTypesTest {

    @Test
    public void testTimestampWithZoneParseWithOffset() {
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00Z"), is(915768000000L));
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00+0300"), is(915757200000L));
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00+03:00"), is(915757200000L));
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00-03:00"), is(915778800000L));

    }
    @Test
    public void testTimestampWithZoneParseWithoutOffset() {
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00"), is(915768000000L));
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00.123456789"), is(915768000123L));
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00+01"), is(915764400000L));
        assertThat(TimestampZType.INSTANCE.value("1999-01-08T04:00:00.123456789+01"), is(915764400123L));
    }

    @Test
    public void testTimestampWithoutZoneParseWithOffset() {
        long expected = 915768000000L;
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00Z"), is(expected));
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00+09:00"), is(expected));
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00+0900"), is(expected));
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00-0100"), is(expected));

    }

    @Test
    public void testTimestampWithoutZoneParseWithoutOffset() {
        long expected = 915768000000L;
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00"), is(expected));
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00.123456789"), is(expected + 123));
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00+01"), is(expected));
        assertThat(TimestampType.INSTANCE.value("1999-01-08T04:00:00.123456789+01:00"), is(expected + 123));
    }
}

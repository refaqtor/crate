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
}

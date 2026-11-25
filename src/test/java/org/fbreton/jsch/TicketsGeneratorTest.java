package org.fbreton.jsch;

import com.google.common.collect.Range;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

class TicketsGeneratorTest extends Assertions {

    @Test
    void ticket_value_always_positive_number_but_less_than_max_opened_channels() {
        var nbtickets = 10;
        var generator = TicketsGenerator.of(nbtickets);
        generator.generator.set(Integer.MIN_VALUE);
        var result = IntStream.of(0, 10).mapToObj(attempt -> generator.get()).collect(Collectors.toList());
        assertThat(result).allMatch(x -> Range.closedOpen(0, nbtickets).contains(x.intValue()));
    }

    @Test
    void cleanup_cache_generator_reset() {
        var nbtickets = 10;
        var ticketsGenerator = TicketsGenerator.of(nbtickets);
        var ticketsGeneratorInternal = ticketsGenerator.generator;
        assertThat(ticketsGeneratorInternal.get()).isZero();
        IntStream.range(0, nbtickets).forEach(key -> ticketsGenerator.get());
        assertThat(ticketsGeneratorInternal.get()).isPositive();
    }
}
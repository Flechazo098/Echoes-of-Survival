package com.flechazo.eos.data.common;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Random;

public sealed interface IntValueOrRange permits IntValueOrRange.Value, IntValueOrRange.Range {
    Codec<IntValueOrRange> CODEC = Codec.either(Codec.INT, Range.CODEC)
            .xmap(
                    either -> either.map(Value::new, r -> r),
                    value -> switch (value) {
                        case Value v -> Either.left(v.value());
                        case Range r -> Either.right(r);
                    }
            );

    int sample(Random random);

    record Value(int value) implements IntValueOrRange {
        @Override
        public int sample(Random random) {
            return value;
        }
    }

    record Range(int min, int max) implements IntValueOrRange {
        public static final Codec<Range> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("min").forGetter(Range::min),
                Codec.INT.fieldOf("max").forGetter(Range::max)
        ).apply(instance, Range::new));

        @Override
        public int sample(Random random) {
            int lo = Math.min(min, max);
            int hi = Math.max(min, max);
            if (lo == hi) return lo;
            int bound = (hi - lo) + 1;
            return lo + random.nextInt(bound);
        }
    }
}

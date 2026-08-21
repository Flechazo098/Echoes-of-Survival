package com.flechazo.eos.util;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.util.OptionalOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;

import java.util.Objects;

/**
 * Bridges between Mojang codecs and the functional types used by the mod.
 */
public final class CodecUtil {
    private CodecUtil() {
    }

    public static <A> Codec<Maybe<A>> maybeCodec(Codec<A> elementCodec) {
        return new MaybeCodec<>(elementCodec);
    }

    public static <A> MapCodec<Maybe<A>> maybeFieldCodec(String fieldName, Codec<A> elementCodec) {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(elementCodec, "elementCodec");
        return elementCodec.optionalFieldOf(fieldName).flatXmap(
                optional -> DataResult.success(OptionalOps.toMaybe(optional)),
                maybe -> Maybe.ofNullable(maybe).fold(
                        () -> DataResult.error(() ->
                                "Cannot encode null as Maybe; use Maybe.none() explicitly"),
                        value -> DataResult.success(OptionalOps.fromMaybe(value))
                )
        );
    }

    private static final class MaybeCodec<A> implements Codec<Maybe<A>> {
        private final Codec<A> elementCodec;

        private MaybeCodec(Codec<A> elementCodec) {
            this.elementCodec = Objects.requireNonNull(elementCodec, "elementCodec");
        }

        @Override
        public <T> DataResult<Pair<Maybe<A>, T>> decode(DynamicOps<T> ops, T input) {
            Objects.requireNonNull(ops, "ops");
            if (input == null) {
                return DataResult.error(() ->
                        "Cannot decode a null DynamicOps value; use DynamicOps.empty() for Maybe.none()");
            }
            if (Objects.equals(input, ops.empty())) {
                return DataResult.success(Pair.of(Maybe.none(), ops.empty()));
            }
            return elementCodec.decode(ops, input).flatMap(decoded ->
                    Maybe.ofNullable(decoded.getFirst()).fold(
                            () -> DataResult.error(() -> "Element codec decoded a null value"),
                            value -> DataResult.success(Pair.of(Maybe.some(value), decoded.getSecond()))
                    ));
        }

        @Override
        public <T> DataResult<T> encode(Maybe<A> input, DynamicOps<T> ops, T prefix) {
            Objects.requireNonNull(ops, "ops");
            if (input == null) {
                return DataResult.error(() ->
                        "Cannot encode null as Maybe; use Maybe.none() explicitly");
            }
            return input.fold(
                    () -> DataResult.success(prefix),
                    value -> elementCodec.encode(value, ops, prefix)
            );
        }
    }
}

package org.leavesmc.leavesclip.mixin;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

record Tuple2<A, B>(A first, B second) {

    @Contract("_ -> new")
    public <C> @NotNull Tuple3<A, B, C> plus(C third) {
        return new Tuple3<>(first, second, third);
    }
}

record Tuple3<A, B, C>(A first, B second, C third) {

    @Contract("_ -> new")
    public <D> @NotNull Tuple4<A, B, C, D> plus(D fourth) {
        return new Tuple4<>(first, second, third, fourth);
    }
}

record Tuple4<A, B, C, D>(A first, B second, C third, D fourth) {

}
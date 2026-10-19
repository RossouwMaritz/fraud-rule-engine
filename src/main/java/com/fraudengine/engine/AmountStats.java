package com.fraudengine.engine;

/** Amount statistics over a customer's transactions in a time window. Mean and stddev are 0 when undefined. */
public record AmountStats(long count, double mean, double stddev) {
}

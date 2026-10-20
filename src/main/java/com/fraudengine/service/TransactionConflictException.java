package com.fraudengine.service;

/** The transaction ID is already stored for a different event. */
public class TransactionConflictException extends RuntimeException {

	public TransactionConflictException(String transactionId) {
		super("Transaction " + transactionId + " is already stored with different details");
	}

}

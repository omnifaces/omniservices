package org.omnifaces.services.pooled;

// TODO move to OmniUtils??
public class UncheckedInterruptedException extends RuntimeException {

	public UncheckedInterruptedException(String message, Throwable cause) {
		super(message, cause);
	}

	public UncheckedInterruptedException(Throwable cause) {
		super(cause);
	}

}

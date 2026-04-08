// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.stack.constants.AbortReason;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.constants.RejectReason;

public class Failure extends Exception {

	public int    abortReason = -1;
	public int    rejectReason = -1;
	public int    errorClass = -1;
	public int    errorCode = -1;
	public String description = null;

    private Failure() {}

	public static class Abort extends Failure {
    	public Abort(int abortReason) { this.abortReason = abortReason; }
		public Abort(int abortReason, String description, Object... descriptionArgs) {
			this(abortReason); this.description = String.format(description, descriptionArgs);
		}
	}
	public static class Reject extends Failure {
		public Reject(int rejectReason) { this.rejectReason = rejectReason; }
		public Reject(int rejectReason, String description, Object... descriptionArgs) {
			this(rejectReason); this.description = String.format(description, descriptionArgs);
		}
	}
	public static class Error extends Failure {
		public Error(int errorClass, int errorCode) { this.errorClass  = errorClass;this.errorCode   = errorCode; }
		public Error(int errorClass, int errorCode, String description, Object... descriptionArgs) {
			this(errorClass,errorCode); this.description = String.format(description, descriptionArgs);
		}
	}

	public String toString() {
		if      (abortReason != -1)  return "Abort "+ AbortReason.toString(abortReason)+"("+abortReason+")"+(description==null?"":" "+description);
		else if (rejectReason != -1) return "Reject "+ RejectReason.toString(rejectReason)+"("+rejectReason+")"+(description==null?"":" "+description);
		else if (errorCode != -1)    return "Error "+ ErrorClass.toString(errorClass)+"("+errorClass+"),"+ ErrorCode.toString(errorCode)+"("+errorCode+")"+(description==null?"":" "+description);
		else                         return "No Failure";
	}


}

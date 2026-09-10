package vn.aims.order.application;

public final class OrderError extends RuntimeException {
    private final int status;
    private final Object responseMessage;
    private final Object issues;
    public OrderError(int status,Object message) { this(status,message,null); }
    public OrderError(int status,Object message,Object issues) { this.status=status;this.responseMessage=message;this.issues=issues; }
    public int status() {return status;}
    public Object responseMessage() {return responseMessage;}
    public Object issues() {return issues;}
}

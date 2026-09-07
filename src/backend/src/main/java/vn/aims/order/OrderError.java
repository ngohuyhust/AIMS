package vn.aims.order;

class OrderError extends RuntimeException {
    final int status;
    final Object message;
    final Object issues;
    OrderError(int status,Object message) { this(status,message,null); }
    OrderError(int status,Object message,Object issues) { this.status=status;this.message=message;this.issues=issues; }
}

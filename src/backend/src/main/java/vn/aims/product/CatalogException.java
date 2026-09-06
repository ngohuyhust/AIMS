package vn.aims.product;

final class CatalogException extends RuntimeException {
    final int status;
    CatalogException(int status, String message) {
        super(message);
        this.status = status;
    }
}

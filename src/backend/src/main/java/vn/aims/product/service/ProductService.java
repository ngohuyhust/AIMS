package vn.aims.product.service;

import vn.aims.product.dto.ProductDetailResponse;
import vn.aims.product.dto.ProductResponse;
import vn.aims.product.dto.ProductSearch;
import vn.aims.product.exception.CatalogException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.product.entity.*;
import vn.aims.product.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository) { this.repository = repository; }

    public List<ProductResponse> search(ProductSearch search) {
        return repository.search(search).stream().map(ProductResponse::from).toList();
    }
    public List<ProductResponse> random() {
        return repository.random().stream().map(ProductResponse::from).toList();
    }
    public ProductDetailResponse detail(int id) {
        Product p = repository.findVisible(id).orElseThrow(() -> new CatalogException(404,"Product with ID " + id + " not found"));
        return switch (p.getProductType().toUpperCase(Locale.ROOT)) {
            case "BOOK" -> ProductDetailResponse.of(p,"book",book(repository.detail(Book.class,id)));
            case "CD" -> ProductDetailResponse.of(p,"cd",cd(repository.detail(Cd.class,id)));
            case "DVD" -> ProductDetailResponse.of(p,"dvd",dvd(repository.detail(Dvd.class,id)));
            case "NEWSPAPER" -> ProductDetailResponse.of(p,"newspaper",newspaper(repository.detail(Newspaper.class,id)));
            default -> throw new CatalogException(400,"Unsupported productType: " + p.getProductType());
        };
    }

    // The legacy API flattens media fields and emits all three aliases, even when null.
    private Map<String,Object> media(Media m) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("productID",m.getProductID());
        result.put("publisher",m.getPublisher());
        result.put("releaseDate",m.getReleaseDate());
        result.put("language",m.getLanguage());
        result.put("genre",m.getGenre());
        result.put("publicationDate",m.getReleaseDate());
        result.put("recordLabel",m.getPublisher());
        result.put("studio",m.getPublisher());
        return result;
    }
    private Map<String,Object> book(Book b) {
        if (b == null) return null;
        var result=media(b.getMedia());
        result.put("authors",b.getAuthors()); result.put("coverType",b.getCoverType()); result.put("numPages",b.getNumPages());
        return result;
    }
    private Map<String,Object> cd(Cd c) {
        if (c == null) return null;
        var result=media(c.getMedia());
        result.put("artists",c.getArtists());
        result.put("tracks",c.getTracks().stream().map(t -> new TrackResponse(t.getId(),t.getTitle(),t.getLengthSeconds())).toList());
        return result;
    }
    private Map<String,Object> dvd(Dvd d) {
        if (d == null) return null;
        var result=media(d.getMedia());
        result.put("discType",d.getDiscType()); result.put("director",d.getDirector());
        result.put("runtimeMinutes",d.getRuntimeMinutes()); result.put("subtitles",d.getSubtitles());
        return result;
    }
    private Map<String,Object> newspaper(Newspaper n) {
        if (n == null) return null;
        var result=media(n.getMedia());
        result.put("editorInChief",n.getEditorInChief()); result.put("issueNumber",n.getIssueNumber());
        result.put("frequency",n.getFrequency()); result.put("issn",n.getIssn()); result.put("sections",n.getSections());
        return result;
    }
    private record TrackResponse(int id, String title, int lengthSeconds) { }
}

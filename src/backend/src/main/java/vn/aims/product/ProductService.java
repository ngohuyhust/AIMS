package vn.aims.product;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return switch (p.productType.toUpperCase(Locale.ROOT)) {
            case "BOOK" -> ProductDetailResponse.of(p,"book",book(repository.detail(Book.class,id)));
            case "CD" -> ProductDetailResponse.of(p,"cd",cd(repository.detail(Cd.class,id)));
            case "DVD" -> ProductDetailResponse.of(p,"dvd",dvd(repository.detail(Dvd.class,id)));
            case "NEWSPAPER" -> ProductDetailResponse.of(p,"newspaper",newspaper(repository.detail(Newspaper.class,id)));
            default -> throw new CatalogException(400,"Unsupported productType: " + p.productType);
        };
    }

    // The legacy API flattens media fields and emits all three aliases, even when null.
    private Map<String,Object> media(Media m) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("productID",m.productID);
        result.put("publisher",m.publisher);
        result.put("releaseDate",m.releaseDate);
        result.put("language",m.language);
        result.put("genre",m.genre);
        result.put("publicationDate",m.releaseDate);
        result.put("recordLabel",m.publisher);
        result.put("studio",m.publisher);
        return result;
    }
    private Map<String,Object> book(Book b) {
        if (b == null) return null;
        var result=media(b.media);
        result.put("authors",b.authors); result.put("coverType",b.coverType); result.put("numPages",b.numPages);
        return result;
    }
    private Map<String,Object> cd(Cd c) {
        if (c == null) return null;
        var result=media(c.media);
        result.put("artists",c.artists);
        result.put("tracks",c.tracks.stream().map(t -> new TrackResponse(t.id,t.title,t.lengthSeconds)).toList());
        return result;
    }
    private Map<String,Object> dvd(Dvd d) {
        if (d == null) return null;
        var result=media(d.media);
        result.put("discType",d.discType); result.put("director",d.director);
        result.put("runtimeMinutes",d.runtimeMinutes); result.put("subtitles",d.subtitles);
        return result;
    }
    private Map<String,Object> newspaper(Newspaper n) {
        if (n == null) return null;
        var result=media(n.media);
        result.put("editorInChief",n.editorInChief); result.put("issueNumber",n.issueNumber);
        result.put("frequency",n.frequency); result.put("issn",n.issn); result.put("sections",n.sections);
        return result;
    }
    private record TrackResponse(int id, String title, int lengthSeconds) { }
}

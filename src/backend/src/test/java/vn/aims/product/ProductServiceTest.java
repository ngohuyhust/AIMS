package vn.aims.product;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.aims.product.dto.*;
import vn.aims.product.exception.*;
import vn.aims.product.service.*;
import vn.aims.product.repository.ProductRepository;

class ProductServiceTest {
    private final ProductRepository repository=mock(ProductRepository.class);
    private final ProductService service=new ProductService(repository);

    @Test
    void missingProductStopsBeforeSubtypeLookup() {
        when(repository.findVisible(42)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(42)).isInstanceOf(CatalogException.class)
                .hasMessage("Product with ID 42 not found");
        verify(repository).findVisible(42);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyCatalogReturnsEmptyList() {
        var search=ProductSearch.parse(null,null,null,null,null,null);
        when(repository.search(search)).thenReturn(List.of());
        assertThat(service.search(search)).isEmpty();
    }

    @Test
    void queryNormalizationPreservesJavascriptNumberBehavior() {
        var search=ProductSearch.parse(" text ","SÁCH"," cd,BOOK,cd,, ","0x10"," ","all");
        assertThat(search.mediaTypes()).containsExactly("CD","BOOK");
        assertThat(search.minPrice()).isEqualByComparingTo("16");
        assertThat(search.maxPrice()).isEqualByComparingTo("0");
        assertThat(ProductSearch.parse(null,null,null,"",null,null).minPrice()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings={"NaN","Infinity","-Infinity","1e999","12x","1d","0x1p2"})
    void nonFiniteOrNonJavascriptNumbersAreRejected(String input) {
        assertThatThrownBy(() -> ProductSearch.parse(null,null,null,input,null,null))
                .isInstanceOf(CatalogException.class).hasMessage("minPrice must be a number");
    }
}

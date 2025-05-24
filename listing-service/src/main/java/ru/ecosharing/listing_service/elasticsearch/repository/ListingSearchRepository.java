package ru.ecosharing.listing_service.elasticsearch.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;
import ru.ecosharing.listing_service.elasticsearch.document.ListingDocument;

@Repository
public interface ListingSearchRepository extends ElasticsearchRepository<ListingDocument, String> {

}
package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of documents. Visibility is decided by the repository queries; this service only turns
 * "not visible" into {@link DocumentNotFoundException}, which is the same for a missing document and
 * someone else's.
 */
@Service
@Transactional(readOnly = true)
public class DocumentQueryService {

    private final DocumentRepository repository;

    DocumentQueryService(DocumentRepository repository) {
        this.repository = repository;
    }

    public List<DocumentEntity> listVisible(Entitlements caller) {
        return repository.findAllVisibleTo(caller);
    }

    public DocumentEntity getVisible(UUID id, Entitlements caller) {
        return repository.findVisibleById(id, caller).orElseThrow(() -> new DocumentNotFoundException(id));
    }

}

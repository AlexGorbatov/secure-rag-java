package com.altronixsoft.securerag.repository;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.Entitlements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that decides which documents a caller can see: their own, plus those shared with any
 * of their groups. Services call the two {@code Entitlements} methods; the queries below them are the
 * building blocks and should not be called directly for access decisions.
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    /**
     * Visible documents, newest first. A caller without a subject sees nothing, even in a shared group.
     */
    default List<DocumentEntity> findAllVisibleTo(Entitlements caller) {
        if (caller.subject() == null) {
            return List.of();
        }
        // An empty IN () list is rendered differently across Hibernate versions; the owner-only
        // query sidesteps it.
        return caller.groups().isEmpty()
                ? findByOwnerSubOrderByCreatedAtDesc(caller.subject())
                : findOwnedOrSharedWith(caller.subject(), caller.groups());
    }

    /**
     * The document if the caller may see it; empty both when it does not exist and when it belongs to
     * someone else, so callers cannot tell the two apart.
     */
    default Optional<DocumentEntity> findVisibleById(UUID id, Entitlements caller) {
        if (caller.subject() == null) {
            return Optional.empty();
        }
        return caller.groups().isEmpty()
                ? findByIdAndOwnerSub(id, caller.subject())
                : findByIdOwnedOrSharedWith(id, caller.subject(), caller.groups());
    }

    List<DocumentEntity> findByOwnerSubOrderByCreatedAtDesc(String ownerSub);

    Optional<DocumentEntity> findByIdAndOwnerSub(UUID id, String ownerSub);

    @Query("""
            select distinct d from DocumentEntity d left join d.allowedGroups g
            where d.ownerSub = :sub or g in :groups
            order by d.createdAt desc
            """)
    List<DocumentEntity> findOwnedOrSharedWith(String sub, Collection<String> groups);

    @Query("""
            select distinct d from DocumentEntity d left join d.allowedGroups g
            where d.id = :id and (d.ownerSub = :sub or g in :groups)
            """)
    Optional<DocumentEntity> findByIdOwnedOrSharedWith(UUID id, String sub, Collection<String> groups);

}

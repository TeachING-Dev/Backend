package com.teaching.backend.domain.material.repository;

public interface FolderMaterialRestoreCountProjection {
    Long getFolderId();

    long getMaterialCount();
}

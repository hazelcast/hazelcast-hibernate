package com.hazelcast.hibernate.entity;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;

@NaturalIdCache
@jakarta.persistence.Entity
@javax.persistence.Entity
@javax.persistence.Table(name = "ANNOTATED_ENTITIES")
@jakarta.persistence.Table(name = "ANNOTATED_ENTITIES")
public class AnnotatedEntity {
    private Long id;

    private String title;

    public AnnotatedEntity() {
    }

    public AnnotatedEntity(String title) {
        this.title = title;
    }

    @NaturalId(mutable = true)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @javax.persistence.Id
    @jakarta.persistence.Id
    @javax.persistence.GeneratedValue(generator = "increment")
    @jakarta.persistence.GeneratedValue(generator = "increment")
    @GenericGenerator(name = "increment", strategy = "increment")
    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }
}

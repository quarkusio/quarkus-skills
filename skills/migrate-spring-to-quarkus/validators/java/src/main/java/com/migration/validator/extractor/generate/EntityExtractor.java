package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.*;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Extracts JPA/Jakarta entities (@Entity, @Embeddable) from an AnalysisResult.
 */
public class EntityExtractor {

    public List<EntityModel> extract(AnalysisResult analysis, Map<String, String> constantsCache) {
        List<EntityModel> entities = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return entities;

        for (Map.Entry<String, JavaCompilationUnit> cuEntry : analysis.symbolTable.entrySet()) {
            JavaCompilationUnit cu = cuEntry.getValue();
            if (cu.typeDeclarations == null)
                continue;

            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                EntityModel entity = buildEntity(typeEntry.getKey(), typeEntry.getValue(), cu, constantsCache);
                if (entity != null)
                    entities.add(entity);
            }
        }
        return entities;
    }

    // -----------------------------------------------------------------------
    // Entity assembly
    // -----------------------------------------------------------------------

    private EntityModel buildEntity(String qname, Type jtype, JavaCompilationUnit cu,
            Map<String, String> constantsCache) {
        List<String> annotations = orEmpty(jtype.annotations);
        if (annotations.stream().noneMatch(AnnotationUtils::isEntityAnnotation))
            return null;

        int dot = qname.lastIndexOf('.');
        EntityModel entity = new EntityModel();
        entity.setOriginalFile(cu.filePath);
        entity.setPackageName(dot >= 0 ? qname.substring(0, dot) : "");
        entity.setClassName(dot >= 0 ? qname.substring(dot + 1) : qname);
        entity.setExtendsClasses(orEmpty(jtype.extendsList));
        entity.setImplementsInterfaces(orEmpty(jtype.implementsList));
        entity.setAnnotations(annotations);

        applyClassAnnotations(entity, annotations, qname, constantsCache);

        List<Field> fields = orEmpty(jtype.fieldDeclarations);
        for (Field field : fields) {
            if (field.variables == null || field.variables.isEmpty())
                continue;
            processField(entity, field, qname, constantsCache);
        }

        entity.setSoftDeleteFlag(AnnotationUtils.detectSoftDelete(fields, annotations));
        return entity;
    }

    private void applyClassAnnotations(EntityModel entity, List<String> annotations,
            String qname, Map<String, String> constantsCache) {
        for (String ann : annotations) {
            if (ann.contains("@Table")) {
                entity.setTableName(AnnotationUtils.extractAnnotationField(ann, "name", constantsCache, qname));
                entity.setIndexes(AnnotationUtils.extractIndexes(ann));
            }
            if (ann.contains("@Inheritance")) {
                String strategy = AnnotationUtils.extractAnnotationField(ann, "strategy");
                if (strategy != null)
                    entity.setInheritanceStrategy(simpleName(strategy));
            }
            if (ann.contains("@NamedQuery") || ann.contains("@NamedQueries")
                    || ann.contains("@NamedNativeQuery") || ann.contains("@NamedNativeQueries")) {
                AnnotationUtils.extractNamedQueries(ann).stream()
                        .map(this::toNamedQueryModel)
                        .forEach(entity.getNamedQueries()::add);
            }
        }
    }

    private NamedQueryModel toNamedQueryModel(Map<String, Object> nq) {
        NamedQueryModel m = new NamedQueryModel();
        m.setName((String) nq.get("name"));
        m.setQuery((String) nq.get("query"));
        m.setType((String) nq.get("type"));
        m.setModifying(Boolean.TRUE.equals(nq.get("is_modifying")));
        @SuppressWarnings("unchecked")
        List<String> params = (List<String>) nq.get("parameters");
        m.setParameters(orEmpty(params));
        return m;
    }

    private void processField(EntityModel entity, Field field,
            String qname, Map<String, String> constantsCache) {
        String fieldName = field.variables.get(0);
        List<String> fa = orEmpty(field.annotations);

        FieldModel fm = new FieldModel();
        fm.setName(fieldName);
        fm.setType(field.type);
        fm.setAnnotations(fa);
        String isNullable = getAnnotationValue(fa, "@Column", "nullable");
        fm.setNullable(isNullable != null ? "true".equals(isNullable) : null);
        String isUnique = getAnnotationValue(fa, "@Column", "unique");
        fm.setUnique(isUnique != null ? "true".equals(isUnique) : null);
        fm.setColumn(getAnnotationValue(fa, "@Column", "name", constantsCache, qname));
        fm.setTransientField(fa.stream().anyMatch(a -> a.contains("@Transient")));
        entity.getFields().add(fm);

        if (fa.stream().anyMatch(a -> a.contains("@Id"))) {
            String strategy = simpleName(getAnnotationValue(fa, "@GeneratedValue", "strategy"));
            String generator = getAnnotationValue(fa, "@GeneratedValue", "generator");
            IdFieldModel idField = new IdFieldModel();
            idField.setName(fieldName);
            idField.setType(field.type);
            idField.setStrategy(strategy);
            idField.setGenerator(generator);
            entity.setIdField(idField);
            IdGenerationModel idGen = new IdGenerationModel();
            idGen.setStrategy(strategy);
            idGen.setGenerator(generator);
            entity.setIdGeneration(idGen);
        }

        if (fa.stream().anyMatch(a -> a.contains("@EmbeddedId"))) {
            entity.setCompositeId(new EntityFieldRef(fieldName, field.type));
        }

        if (fa.stream().anyMatch(a -> a.contains("@Version"))) {
            entity.setVersionField(new EntityFieldRef(fieldName, field.type));
        }

        String joinColAnn = fa.stream().filter(a -> a.contains("@JoinColumn")).findFirst().orElse(null);
        for (String ann : fa) {
            for (String rel : Arrays.asList("OneToMany", "ManyToOne", "ManyToMany", "OneToOne")) {
                if (!ann.contains("@" + rel))
                    continue;
                String[] normalized = AnnotationUtils.normalizeType(field.type);
                String fetch = AnnotationUtils.extractAnnotationField(ann, "fetch");
                RelationshipModel rm = new RelationshipModel();
                rm.setType(rel);
                rm.setCollectionType(normalized[0]);
                rm.setTargetEntity(normalized[1]);
                rm.setMappedBy(AnnotationUtils.extractAnnotationField(ann, "mappedBy", constantsCache, qname));
                rm.setFetch(simpleName(fetch));
                rm.setCascade(AnnotationUtils.parseCascade(ann));
                rm.setColumn(joinColAnn != null
                        ? AnnotationUtils.extractAnnotationField(joinColAnn, "name", constantsCache, qname)
                        : null);
                entity.getRelationships().add(rm);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Micro-helpers
    // -----------------------------------------------------------------------

    /** Returns the list if non-null, otherwise an empty list. */
    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    /**
     * Returns the simple name after the last '.', or the value itself if there is
     * no dot.
     */
    private static String simpleName(String s) {
        if (s == null)
            return null;
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private String getAnnotationValue(List<String> annotations, String target, String key) {
        return getAnnotationValue(annotations, target, key, null, null);
    }

    private String getAnnotationValue(List<String> annotations, String target, String key,
            Map<String, String> cache, String currentClass) {
        for (String ann : annotations) {
            if (ann.contains(target))
                return AnnotationUtils.extractAnnotationField(ann, key, cache, currentClass);
        }
        return null;
    }
}

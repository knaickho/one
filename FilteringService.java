package xyz.app;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class FilteringService {

    public static void main(String[] args) {

        Function<Number, Collection<UUID>> uuidGenerator = limit -> Stream.generate(UUID::randomUUID)
                .limit(limit.longValue())
                .collect(Collectors.toSet());

        var criteria = SearchCriteria.builder()
//                .ids(uuidGenerator.apply(10))
                .managers(uuidGenerator.apply(1))
                .subdivisions(uuidGenerator.apply(10))
                .build();

        log(">>> REQUEST: {}", criteria);

        var result = new FilteringService().findAndFilterItServiceIdsByCriteria(criteria);

        log(">>> RESULT: {}, {}", result, result.size());

    }

    public @NonNull Collection<UUID> findAndFilterItServiceIdsByCriteria(SearchCriteria criteria) {

        // если в исходном запросе только коллекция 'ids' не пустая - возвращаем 'ids'
        // если совсем пусто - возвращаем пустую коллекцию
        if (criteria == null || containsOnlyItServiceIds(criteria)) {
            return Optional.ofNullable(criteria)
                    .map(SearchCriteria::getIds)
                    .orElseGet(HashSet::new);
        }

        // находим подходящие ИТ-услуги по критериям, кроме UUID ИТ-услуг
        var foundServices = fetchServicesMatchingCriteria(criteria);

        // если в исходном запросе только одна коллекция не пустая - вытаскиваем UUID ИТ-сервисов и возвращаем результат
        if (hasSingleNonEmptyCriterion(criteria)) {
            return foundServices.stream()
                    .map(ItServiceDto::getId)
                    .collect(Collectors.toSet());
        }

        // создаем новую коллекцию прототипов ИТ-услуг
        // кладём в них значения атрибутов, которые должны обязательно присутствовать в результате
        var patterns = buildMatchPatterns(criteria);

        // проверяем наличие нужных атрибутов, фильтруем и возвращаем результат
        return foundServices.stream()
                .filter(matchesPatterns(patterns))
                .map(ItServiceDto::getId)
                .filter(Objects::nonNull)
                .filter(id -> CollectionUtils.isEmpty(criteria.getIds()) || criteria.getIds().contains(id))
                .collect(Collectors.toSet());
    }

    private boolean containsOnlyItServiceIds(SearchCriteria criteria) {
        return Stream.<Function<SearchCriteria, Collection<UUID>>>of(
                        SearchCriteria::getManagers, SearchCriteria::getSubdivisions
                )
                .map(getter -> getter.apply(criteria))
                .allMatch(CollectionUtils::isEmpty);
    }

    private Collection<ItServiceDto> buildMatchPatterns(SearchCriteria criteria) {
        return Stream.of(new ItServiceDto())
                .flatMap(emitWithAttributeValues(criteria::getManagers, ItServiceDto.ItServiceDtoBuilder::manager))
                .flatMap(emitWithAttributeValues(criteria::getSubdivisions, ItServiceDto.ItServiceDtoBuilder::subdivision))
                .collect(Collectors.toSet());
    }

    private Predicate<ItServiceDto> matchesPatterns(Collection<ItServiceDto> models) {
        return found -> models.stream().anyMatch(equalsByNonNullManagerAndNonNullSubdivision(found));
    }

    private Predicate<ItServiceDto> equalsByNonNullManagerAndNonNullSubdivision(ItServiceDto found) {
        return model -> EqualityUtil.equalsByCertainAttributes(
                model, found, fetchNonNullManagersAndNonNullSubdivisions(model)
        );
    }

    private Collection<Function<ItServiceDto, ?>> fetchNonNullManagersAndNonNullSubdivisions(ItServiceDto model) {
        return Stream.<Function<ItServiceDto, UUID>>of(
                        ItServiceDto::getManager, ItServiceDto::getSubdivision
                )
                .filter(getter -> getter.apply(model) != null)
                .collect(Collectors.toSet());
    }

    private <ATTRIBUTE> Function<ItServiceDto, Stream<ItServiceDto>> emitWithAttributeValues(
            Supplier<Collection<ATTRIBUTE>> attributeProvider,
            BiFunction<ItServiceDto.ItServiceDtoBuilder, ATTRIBUTE, ItServiceDto.ItServiceDtoBuilder> attributeAssigner
    ) {
        return itService -> Optional.ofNullable(attributeProvider.get())
                .filter(CollectionUtils::isNotEmpty)
                .map(attributes -> attributes.stream()
                        .map(attribute -> attributeAssigner.apply(itService.toBuilder(), attribute))
                        .map(ItServiceDto.ItServiceDtoBuilder::build)
                )
                .orElse(Stream.of(itService));
    }

    private Collection<ItServiceDto> fetchServicesMatchingCriteria(SearchCriteria criteria) {

        // mock the search, particulars are not really important for now

        if (criteria.getIds() == null) criteria.setIds(new HashSet<>());
        if (criteria.getManagers() == null) criteria.setManagers(new HashSet<>());
        if (criteria.getSubdivisions() == null) criteria.setSubdivisions(new HashSet<>());

        Consumer<Collection<UUID>> assigner = collection -> Stream.generate(UUID::randomUUID)
                .limit(0L)
                .forEach(collection::add);

        Stream.<Supplier<Collection<UUID>>>of(
                        criteria::getIds,
                        criteria::getManagers,
                        criteria::getSubdivisions
                )
                .map(Supplier::get)
                .forEach(assigner);

        return Stream.of(new ItServiceDto())
                .flatMap(emitWithAttributeValues(criteria::getIds, ItServiceDto.ItServiceDtoBuilder::id))
                .flatMap(emitWithAttributeValues(criteria::getManagers, ItServiceDto.ItServiceDtoBuilder::manager))
                .flatMap(emitWithAttributeValues(criteria::getSubdivisions, ItServiceDto.ItServiceDtoBuilder::subdivision))
                .map(itService -> itService.getId() != null ? itService : itService.setId(UUID.randomUUID()))
                .collect(Collectors.toSet());
    }

    private <EXEMPLAR, ATTRIBUTE, ACCEPTOR> Function<EXEMPLAR, Stream<? extends EXEMPLAR>> emitExemplarWithAttributes_1(
            Supplier<Collection<ATTRIBUTE>> attributeProvider,
            BiFunction<ACCEPTOR, ATTRIBUTE, ACCEPTOR> attributeAssigner,
            Function<EXEMPLAR, ACCEPTOR> emitter,
            Function<ACCEPTOR, EXEMPLAR> sealer
    ) {
        return exemplar -> Optional.ofNullable(attributeProvider.get())
                .filter(CollectionUtils::isNotEmpty)
                .map(attributes -> attributes.stream()
                        .map(attribute -> attributeAssigner.apply(emitter.apply(exemplar), attribute))
                        .map(sealer)
                )
                .orElse(Stream.of(exemplar));
    }

    private boolean hasSingleNonEmptyCriterion(SearchCriteria criteria) {
        return Stream.<Function<SearchCriteria, Collection<UUID>>>of(
                        SearchCriteria::getIds, SearchCriteria::getManagers, SearchCriteria::getSubdivisions
                )
                .map(getter -> getter.apply(criteria))
                .filter(CollectionUtils::isNotEmpty)
                .count() == 1L;
    }

    private static void log(String message, Object... args) {
        System.out.printf(message.replace("{}", "%s"), args);
        System.out.println();
    }
}

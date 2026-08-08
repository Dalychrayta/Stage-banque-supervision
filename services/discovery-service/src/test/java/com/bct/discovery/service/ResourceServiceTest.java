package com.bct.discovery.service;

import com.bct.discovery.dto.RegisterResourceRequest;
import com.bct.discovery.dto.ResourceDTO;
import com.bct.discovery.kafka.ResourceEventProducer;
import com.bct.discovery.model.Resource;
import com.bct.discovery.model.ResourceStatus;
import com.bct.discovery.model.ResourceType;
import com.bct.discovery.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResourceServiceTest {

    private ResourceRepository repository;
    private ResourceEventProducer eventProducer;
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        repository = mock(ResourceRepository.class);
        eventProducer = mock(ResourceEventProducer.class);
        resourceService = new ResourceService(repository, eventProducer);

        when(repository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            if (r.getId() == null) r.setId(1L);
            return r;
        });
    }

    private RegisterResourceRequest sampleRequest() {
        RegisterResourceRequest req = new RegisterResourceRequest();
        req.setResourceId("srv-100");
        req.setName("test-server");
        req.setType(ResourceType.SERVER);
        req.setEnvironment("DEV");
        return req;
    }

    @Test
    void register_shouldCreateNewResourceWithUnknownStatusAndPublishEvent() {
        when(repository.existsByResourceId("srv-100")).thenReturn(false);

        ResourceDTO result = resourceService.register(sampleRequest());

        assertThat(result.getResourceId()).isEqualTo("srv-100");
        assertThat(result.getStatus()).isEqualTo(ResourceStatus.UNKNOWN);
        verify(eventProducer, times(1)).sendResourceDiscovered(any(ResourceDTO.class));
    }

    @Test
    void register_shouldUpdateExistingResourceWithoutDuplicating() {
        Resource existing = Resource.builder()
                .id(5L).resourceId("srv-100").name("old-name")
                .type(ResourceType.SERVER).status(ResourceStatus.UP)
                .createdAt(LocalDateTime.now())
                .build();
        when(repository.existsByResourceId("srv-100")).thenReturn(true);
        when(repository.findByResourceId("srv-100")).thenReturn(Optional.of(existing));

        RegisterResourceRequest req = sampleRequest();
        req.setName("updated-name");
        ResourceDTO result = resourceService.register(req);

        assertThat(result.getName()).isEqualTo("updated-name");
        assertThat(result.getStatus()).isEqualTo(ResourceStatus.UP); // le statut existant n'est pas écrasé
        verify(eventProducer, never()).sendResourceDiscovered(any());
    }

    @Test
    void findById_shouldThrowWhenResourceMissing() {
        when(repository.findByResourceId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.findById("ghost"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateStatus_shouldPublishEventOnlyWhenStatusActuallyChanges() {
        Resource existing = Resource.builder()
                .id(2L).resourceId("srv-200").name("srv").type(ResourceType.SERVER)
                .status(ResourceStatus.UP).createdAt(LocalDateTime.now())
                .build();
        when(repository.findByResourceId("srv-200")).thenReturn(Optional.of(existing));

        // Changement réel UP -> DOWN : un event doit partir
        resourceService.updateStatus("srv-200", ResourceStatus.DOWN);
        verify(eventProducer, times(1)).sendResourceStatusChanged(any(), eq("UP"));

        // Pas de changement DOWN -> DOWN : aucun nouvel event
        resourceService.updateStatus("srv-200", ResourceStatus.DOWN);
        verify(eventProducer, times(1)).sendResourceStatusChanged(any(), any());
    }

    @Test
    void getStats_shouldAggregateCountsPerStatus() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByStatus(ResourceStatus.UP)).thenReturn(6L);
        when(repository.countByStatus(ResourceStatus.DOWN)).thenReturn(1L);
        when(repository.countByStatus(ResourceStatus.DEGRADED)).thenReturn(2L);
        when(repository.countByStatus(ResourceStatus.UNKNOWN)).thenReturn(1L);

        var stats = resourceService.getStats();

        assertThat(stats)
                .containsEntry("total", 10L)
                .containsEntry("up", 6L)
                .containsEntry("down", 1L)
                .containsEntry("degraded", 2L)
                .containsEntry("unknown", 1L);
    }

    @Test
    void delete_shouldThrowWhenResourceMissing() {
        when(repository.findByResourceId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.delete("ghost"))
                .isInstanceOf(NoSuchElementException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void findByType_shouldDelegateToRepository() {
        List<Resource> resources = List.of(
                Resource.builder().resourceId("db-1").type(ResourceType.DATABASE).status(ResourceStatus.UP).createdAt(LocalDateTime.now()).build()
        );
        when(repository.findByType(ResourceType.DATABASE)).thenReturn(resources);

        List<ResourceDTO> result = resourceService.findByType(ResourceType.DATABASE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResourceId()).isEqualTo("db-1");
    }
}

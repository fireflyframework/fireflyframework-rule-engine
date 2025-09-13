/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.rules.core.services.impl;

import com.firefly.common.core.filters.FilterRequest;
import com.firefly.common.core.filters.FilterUtils;
import com.firefly.common.core.queries.PaginationResponse;
import com.firefly.rules.core.mappers.ConstantMapper;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.models.entities.Constant;
import com.firefly.rules.models.repositories.ConstantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ConstantServiceImpl implements ConstantService {

    @Autowired
    private ConstantRepository repository;

    @Autowired
    private ConstantMapper mapper;

    @Override
    public Mono<PaginationResponse<ConstantDTO>> filterConstants(FilterRequest<ConstantDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Constant.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ConstantDTO> createConstant(ConstantDTO constantDTO) {
        return Mono.just(constantDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ConstantDTO> updateConstant(UUID constantId, ConstantDTO constantDTO) {
        return repository.findById(constantId)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with ID: " + constantId)))
                .flatMap(existingConstant -> {
                    // Set the ID to ensure we're updating the correct entity
                    constantDTO.setId(constantId);
                    Constant updatedEntity = mapper.toEntity(constantDTO);
                    // Preserve audit fields
                    updatedEntity.setCreatedAt(existingConstant.getCreatedAt());
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteConstant(UUID constantId) {
        return repository.findById(constantId)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with ID: " + constantId)))
                .flatMap(constant -> repository.deleteById(constantId));
    }

    @Override
    public Mono<ConstantDTO> getConstantById(UUID constantId) {
        return repository.findById(constantId)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with ID: " + constantId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ConstantDTO> getConstantByCode(String code) {
        return repository.findByCode(code)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with code: " + code)))
                .map(mapper::toDTO);
    }

    @Override
    public Flux<ConstantDTO> getConstantsByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(codes)
                .flatMap(code -> repository.findByCode(code)
                        .map(mapper::toDTO)
                        .onErrorResume(error -> {
                            // Log the error but don't fail the entire operation
                            // This allows partial loading of constants
                            return Mono.empty();
                        }));
    }
}

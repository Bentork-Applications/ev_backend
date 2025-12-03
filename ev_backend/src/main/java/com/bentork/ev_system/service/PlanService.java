package com.bentork.ev_system.service;

import com.bentork.ev_system.dto.request.PlanDTO;
import com.bentork.ev_system.mapper.PlanMapper;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Plan;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.PlanRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlanService {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private AdminRepository adminRepository;

    public PlanDTO createPlan(PlanDTO dto, Long adminId) {
        try {
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

            Plan plan = PlanMapper.toEntity(dto);
            plan.setCreatedBy(admin);

            Plan saved = planRepository.save(plan);
            log.info("Plan created: id={}, planName={}, adminId={}",
                    saved.getId(), saved.getPlanName(), adminId);

            return PlanMapper.toDTO(saved);
        } catch (EntityNotFoundException e) {
            log.error("Failed to create plan - Admin not found: adminId={}", adminId);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create plan: planName={}, adminId={}: {}",
                    dto.getPlanName(), adminId, e.getMessage(), e);
            throw e;
        }
    }

    public List<PlanDTO> getAllPlans() {
        try {
            List<PlanDTO> plans = planRepository.findAll()
                    .stream()
                    .map(PlanMapper::toDTO)
                    .collect(Collectors.toList());

            if (log.isDebugEnabled()) {
                log.debug("Retrieved {} plans", plans.size());
            }

            return plans;
        } catch (Exception e) {
            log.error("Failed to retrieve all plans: {}", e.getMessage(), e);
            throw e;
        }
    }

    public PlanDTO getPlanById(Long id) {
        try {
            Plan plan = planRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Plan not found"));

            if (log.isDebugEnabled()) {
                log.debug("Retrieved plan: id={}, planName={}", id, plan.getPlanName());
            }

            return PlanMapper.toDTO(plan);
        } catch (EntityNotFoundException e) {
            log.warn("Plan not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve plan: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    public PlanDTO updatePlan(Long id, PlanDTO dto) {
        try {
            Plan plan = planRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Plan not found"));

            plan.setPlanName(dto.getPlanName());
            plan.setDescription(dto.getDescription());
            plan.setDurationMin(dto.getDurationMin());
            plan.setWalletDeduction(dto.getWalletDeduction());
            plan.setChargerType(dto.getChargerType());
            plan.setRate(dto.getRate());

            Plan updated = planRepository.save(plan);
            log.info("Plan updated: id={}, planName={}", id, updated.getPlanName());

            return PlanMapper.toDTO(updated);
        } catch (EntityNotFoundException e) {
            log.warn("Failed to update plan - Plan not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to update plan: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    public void deletePlan(Long id) {
        try {
            if (!planRepository.existsById(id)) {
                throw new EntityNotFoundException("Plan not found");
            }
            planRepository.deleteById(id);
            log.info("Plan deleted: id={}", id);
        } catch (EntityNotFoundException e) {
            log.warn("Failed to delete plan - Plan not found: id={}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete plan: id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }
}
package com.microcourse.service;
import com.microcourse.dto.microSpecialty.*;
import java.util.List;
public interface MicroSpecialtyProposalService {
    MicroSpecialtyProposalVO create(MicroSpecialtyProposalRequest request);
    List<MicroSpecialtyProposalVO> getMy();
    List<MicroSpecialtyProposalVO> getAll();
    MicroSpecialtyProposalVO approve(Long id);
    void reject(Long id, String reason);
    void withdraw(Long id);
    MicroSpecialtyProposalVO resubmit(Long id, MicroSpecialtyProposalRequest request);
}

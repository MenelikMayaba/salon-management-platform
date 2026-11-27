package za.co.salon.catalogue.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.catalogue.dto.TaxCodeRequest;
import za.co.salon.catalogue.dto.TaxCodeResponse;
import za.co.salon.catalogue.entity.TaxCode;
import za.co.salon.catalogue.repository.TaxCodeRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

@Service
public class TaxCodeService {

    private final TaxCodeRepository taxCodeRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public TaxCodeService(TaxCodeRepository taxCodeRepository,
                          BusinessAuthorizationService authz,
                          BusinessContext businessContext) {
        this.taxCodeRepository = taxCodeRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public TaxCodeResponse create(TaxCodeRequest request) {
        TaxCode taxCode = new TaxCode();
        taxCode.setBusinessId(businessContext.getBusinessId());
        applyRequest(taxCode, request);
        return toResponse(taxCodeRepository.save(taxCode));
    }

    @Transactional(readOnly = true)
    public Page<TaxCodeResponse> list(Pageable pageable) {
        Page<TaxCode> page = taxCodeRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public TaxCodeResponse update(Long id, TaxCodeRequest request) {
        TaxCode taxCode = taxCodeRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax code not found"));
        authz.assertBusinessOwnership(taxCode);
        applyRequest(taxCode, request);
        return toResponse(taxCodeRepository.save(taxCode));
    }

    private void applyRequest(TaxCode taxCode, TaxCodeRequest request) {
        taxCode.setName(request.getName());
        taxCode.setRate(request.getRate());
        taxCode.setTaxType(parseTaxType(request.getTaxType()));
        taxCode.setActive(request.isActive());
    }

    private TaxCode.TaxType parseTaxType(String value) {
        try {
            return TaxCode.TaxType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown tax type: " + value + " (valid: VAT, NONE, EXEMPT)");
        }
    }

    private TaxCodeResponse toResponse(TaxCode taxCode) {
        TaxCodeResponse response = new TaxCodeResponse();
        response.setId(taxCode.getId());
        response.setName(taxCode.getName());
        response.setRate(taxCode.getRate());
        response.setTaxType(taxCode.getTaxType().name());
        response.setActive(taxCode.isActive());
        return response;
    }
}

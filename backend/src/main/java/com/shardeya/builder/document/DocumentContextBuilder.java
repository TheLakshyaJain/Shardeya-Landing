package com.shardeya.builder.document;

import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentRecordRepository;
import com.shardeya.builder.payment.PaymentSchedule;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.media.MediaService;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianAmountInWords;
import com.shardeya.shared.IndianTime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * B-11 §7 "same context builder for preview and generation" (this codebase's
 * own established M-10 pattern of "the same query powers preview and
 * export, so the two can never disagree", applied here to context data
 * instead of a report query). Every returned value is a plain Java
 * String/Number -- never raw HTML -- since {@link TemplateRenderer} always
 * HTML-escapes on interpolation regardless.
 */
@Service
public class DocumentContextBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final PlotSaleRepository plotSaleRepository;
    private final PlotRepository plotRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final MediaService mediaService;
    private final TenantContextBinder tenantContextBinder;

    public DocumentContextBuilder(PlotSaleRepository plotSaleRepository, PlotRepository plotRepository,
                                   ProjectRepository projectRepository, OrganizationRepository organizationRepository,
                                   AppUserRepository appUserRepository, PaymentScheduleRepository scheduleRepository,
                                   PaymentRecordRepository paymentRecordRepository, MediaService mediaService,
                                   TenantContextBinder tenantContextBinder) {
        this.plotSaleRepository = plotSaleRepository;
        this.plotRepository = plotRepository;
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.scheduleRepository = scheduleRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.mediaService = mediaService;
        this.tenantContextBinder = tenantContextBinder;
    }

    public Map<String, Object> buildContext(DocumentTemplate.DocType docType, UUID entityId) {
        return switch (docType) {
            case ALLOTMENT_LETTER -> forSale(loadSale(entityId), null);
            case DEMAND_LETTER -> forDemand(loadSale(entityId));
            case PAYMENT_RECEIPT -> forReceipt(loadPaymentRecord(entityId));
            case BOOKING_CONFIRMATION -> throw new ResourceNotFoundException("error.documentTemplate.docTypeNotSupported");
        };
    }

    // entityType tag stored on generated_document -- kept alongside the
    // context builder since the two are the same "what does this doc type
    // point at" decision.
    public String entityTypeFor(DocumentTemplate.DocType docType) {
        return switch (docType) {
            case ALLOTMENT_LETTER, DEMAND_LETTER -> "PLOT_SALE";
            case PAYMENT_RECEIPT -> "PAYMENT_RECORD";
            case BOOKING_CONFIRMATION -> "PLOT_SALE";
        };
    }

    private PlotSale loadSale(UUID id) {
        PlotSale sale = plotSaleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (!sale.getOrgId().equals(tenantContextBinder.currentOrgId())) throw new ResourceNotFoundException("error.notFound");
        return sale;
    }

    private PaymentRecord loadPaymentRecord(UUID id) {
        PaymentRecord record = paymentRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (!record.getOrgId().equals(tenantContextBinder.currentOrgId())) throw new ResourceNotFoundException("error.notFound");
        return record;
    }

    private Map<String, Object> forSale(PlotSale sale, PaymentRecord ignored) {
        Map<String, Object> ctx = baseContext(sale.getProjectId(), sale.getPlotId(), sale.getBuyerName(), sale.getBuyerMobile(), sale.getBuyerEmail());
        ctx.put("sale", Map.of(
                "dealValue", money(sale.getDealValue()),
                "dealValueWords", IndianAmountInWords.convert(sale.getDealValue()),
                "purchaseDate", sale.getPurchaseDate().format(DATE_FMT),
                "paymentType", sale.getPaymentType().name()
        ));
        List<PaymentSchedule> rows = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.getId());
        List<Map<String, Object>> schedule = new ArrayList<>();
        for (PaymentSchedule row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("label", row.getLabel());
            r.put("amount", money(row.getExpectedAmount()));
            r.put("dueDate", row.getDueDate().format(DATE_FMT));
            schedule.add(r);
        }
        ctx.put("schedule", schedule);
        return ctx;
    }

    private Map<String, Object> forDemand(PlotSale sale) {
        Map<String, Object> ctx = baseContext(sale.getProjectId(), sale.getPlotId(), sale.getBuyerName(), sale.getBuyerMobile(), sale.getBuyerEmail());
        LocalDate today = IndianTime.today();
        List<PaymentSchedule> rows = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.getId());
        List<Map<String, Object>> overdue = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentSchedule row : rows) {
            boolean isOverdue = row.getDueDate().isBefore(today)
                    && (row.getStatus() == PaymentSchedule.Status.PENDING || row.getStatus() == PaymentSchedule.Status.PARTIALLY_PAID || row.getStatus() == PaymentSchedule.Status.OVERDUE);
            if (!isOverdue) continue;
            BigDecimal due = row.getExpectedAmount().subtract(row.getAmountAllocated());
            total = total.add(due);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("label", row.getLabel());
            r.put("amount", money(due));
            r.put("dueDate", row.getDueDate().format(DATE_FMT));
            r.put("daysOverdue", ChronoUnit.DAYS.between(row.getDueDate(), today));
            overdue.add(r);
        }
        ctx.put("overdue", overdue);
        ctx.put("demand", Map.of(
                "totalOverdueAmount", money(total),
                "totalOverdueAmountWords", IndianAmountInWords.convert(total)
        ));
        return ctx;
    }

    private Map<String, Object> forReceipt(PaymentRecord record) {
        PlotSale sale = loadSale(record.getPlotSaleId());
        Map<String, Object> ctx = baseContext(sale.getProjectId(), sale.getPlotId(), sale.getBuyerName(), sale.getBuyerMobile(), sale.getBuyerEmail());
        ctx.put("payment", Map.of(
                "receiptNo", record.getReceiptNo(),
                "amount", money(record.getAmount()),
                "amountWords", IndianAmountInWords.convert(record.getAmount()),
                "paidOn", record.getPaidOn().format(DATE_FMT),
                "mode", record.getMode().name(),
                "reference", record.getReference() == null ? "" : record.getReference(),
                "totalPaid", money(sale.getTotalPaid()),
                "balanceDue", money(sale.getBalanceDue())
        ));
        return ctx;
    }

    private Map<String, Object> baseContext(UUID projectId, UUID plotId, String buyerName, String buyerMobile, String buyerEmail) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        Plot plot = plotRepository.findByIdAndDeletedAtIsNull(plotId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        Organization org = organizationRepository.findById(tenantContextBinder.currentOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        String ownerPhone = appUserRepository.findFirstByOrgIdAndOwnerTrueAndDeletedAtIsNull(org.getId())
                .map(AppUser::getMobile).orElse("");
        String logoUrl = org.getLogoMediaId() != null ? safeLogoUrl(org.getLogoMediaId()) : "";

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("org", Map.of("name", org.getName(), "address", org.getCity(), "phone", ownerPhone, "logoUrl", logoUrl));
        ctx.put("project", Map.of("name", project.getName(), "address", project.getAddress() == null ? "" : project.getAddress()));
        ctx.put("plot", Map.of(
                "number", plot.getPlotNumber(),
                "areaSqft", money(plot.getSizeSqft()),
                "areaValue", plot.getSizeValue().toPlainString(),
                "areaUnit", plot.getSizeUnit(),
                "facing", plot.getFacing() == null ? "" : formatFacing(plot.getFacing())
        ));
        ctx.put("buyer", Map.of("name", buyerName, "mobile", buyerMobile, "email", buyerEmail == null ? "" : buyerEmail));
        ctx.put("document", new HashMap<>(Map.of("generatedDate", IndianTime.today().format(DATE_FMT))));
        return ctx;
    }

    private String safeLogoUrl(UUID logoMediaId) {
        try {
            return mediaService.get(logoMediaId).url();
        } catch (Exception e) {
            // B-11 §10 "org with no logo -> text-based letterhead fallback
            // rather than a broken image" -- also covers a logo that failed
            // to resolve for any other reason.
            return "";
        }
    }

    // Plot.Facing's actual constants are short codes (N/S/E/W/NE/NW/SE/SW,
    // matching the plot_facing Postgres enum) -- NOT compass-direction
    // words, which a first draft of this method wrongly assumed (splitting
    // on "_" against names like "NORTH_EAST" that don't exist here).
    private static final Map<Plot.Facing, String> FACING_LABELS = Map.of(
            Plot.Facing.N, "North", Plot.Facing.S, "South", Plot.Facing.E, "East", Plot.Facing.W, "West",
            Plot.Facing.NE, "North East", Plot.Facing.NW, "North West", Plot.Facing.SE, "South East", Plot.Facing.SW, "South West");

    private String formatFacing(Plot.Facing facing) {
        return FACING_LABELS.getOrDefault(facing, facing.name());
    }

    private String money(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}

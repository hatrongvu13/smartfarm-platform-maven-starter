package com.htv.farmverse.farm.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root "trang trại". Mọi thay đổi chuồng đi qua đây để giữ luật:
 * <ul>
 *   <li>mã chuồng không trùng trong một farm;</li>
 *   <li>farm đã ARCHIVED không nhận thêm chuồng;</li>
 *   <li>tổng sức chứa = tổng sức chứa các chuồng (tính, không lưu).</li>
 * </ul>
 * Lớp này không biết Spring, JPA hay tenant: tenant do lớp adapter lo.
 */
public final class Farm {

    private final String id;
    private final String code;
    private String name;
    private String address;
    private FarmStatus status;
    private final List<Barn> barns;
    private final long version;

    private Farm(String id, String code, String name, String address, FarmStatus status, List<Barn> barns, long version) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.address = address;
        this.status = status;
        this.barns = new ArrayList<>(barns);
        this.version = version;
    }

    /** Tạo farm mới. */
    public static Farm create(String code, String name, String address) {
        if (code == null || !code.matches("[a-z0-9][a-z0-9-]{1,49}")) {
            throw new FarmRuleViolation("SF-FARM-001", "Mã farm viết thường, 2-50 ký tự (a-z, 0-9, -)");
        }
        requireName(name);
        return new Farm(UUID.randomUUID().toString(), code, name, address, FarmStatus.ACTIVE, List.of(), 0);
    }

    /** Dựng lại từ dữ liệu đã lưu (adapter dùng). */
    public static Farm restore(String id, String code, String name, String address, FarmStatus status,
                               List<Barn> barns, long version) {
        return new Farm(id, code, name, address, status, barns, version);
    }

    public void rename(String name, String address) {
        requireName(name);
        this.name = name;
        this.address = address;
    }

    public Barn addBarn(String code, String name, int capacity) {
        if (status == FarmStatus.ARCHIVED) {
            throw new FarmRuleViolation("SF-FARM-006", "Farm đã ngừng hoạt động");
        }
        if (barns.stream().anyMatch(b -> b.code().equals(code))) {
            throw new FarmRuleViolation("SF-FARM-007", "Mã chuồng " + code + " đã có trong farm");
        }
        Barn barn = new Barn(code, name, capacity);
        barns.add(barn);
        return barn;
    }

    public void removeBarn(String code) {
        if (!barns.removeIf(b -> b.code().equals(code))) {
            throw new FarmRuleViolation("SF-FARM-008", "Không có chuồng " + code);
        }
    }

    public void archive() {
        status = FarmStatus.ARCHIVED;
    }

    public int totalCapacity() {
        return barns.stream().mapToInt(Barn::capacity).sum();
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new FarmRuleViolation("SF-FARM-002", "Tên farm 1-200 ký tự");
        }
    }

    public String id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public String address() { return address; }
    public FarmStatus status() { return status; }
    public List<Barn> barns() { return Collections.unmodifiableList(barns); }
    public long version() { return version; }
}

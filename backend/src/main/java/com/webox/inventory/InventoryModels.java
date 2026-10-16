package com.webox.inventory;
import java.time.*;
import java.util.List;
public final class InventoryModels {
    private InventoryModels() {}
    public record Entry(String dishId,int remaining,long version) {}
    public record Snapshot(LocalDate deliveryDate,long menuRevision,List<Entry> items,Instant serverTime) {}
    public record Updated(LocalDate deliveryDate,long menuRevision,String dishId,int remaining,long version,Instant occurredAt) {}
    public record Changed(LocalDate deliveryDate,long menuRevision) {}
}

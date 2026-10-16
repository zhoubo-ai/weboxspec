package com.webox.ordering;
import java.sql.*;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import com.webox.shared.DomainTypes.Allergen;
import static com.webox.ordering.OrderingModels.*;
final class SnapshotRows {
    private SnapshotRows() {}
    static PricedLine read(ResultSet rs,JsonMapper json,String versionColumn) throws SQLException {
        return new PricedLine(rs.getString("dish_id"),rs.getString("dish_name_snapshot"),rs.getLong(versionColumn),
            List.of(json.readValue(rs.getString("options_snapshot"),OptionSnapshot[].class)),rs.getInt("quantity"),rs.getLong("base_price_cents"),rs.getLong("unit_price_cents"),rs.getLong("subtotal_cents"),List.of(json.readValue(rs.getString("effective_allergens_snapshot"),Allergen[].class)));
    }
}

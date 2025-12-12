package com.example.core_service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DbController {

    private final NamedParameterJdbcTemplate jdbc;

    public DbController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // простая проверка имени таблицы/представления
    private void validateTableName(String table) {
        if (!table.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
    }

    // /api/tables/{table}
    @GetMapping("/tables/{table}")
    public List<Map<String, Object>> getTable(@PathVariable String table) {
        validateTableName(table);
        String sql = "SELECT * FROM public." + table;
        return jdbc.getJdbcTemplate().queryForList(sql);
    }

    // добавить/обновить запись
    @PostMapping("/tables/{table}")
    public void saveRow(
            @PathVariable String table,
            @RequestBody Map<String, Object> row
    ) {
        validateTableName(table);

        Object id = row.get("id");
        Map<String, Object> fields = new java.util.HashMap<>(row);
        fields.remove("id");

        if (id == null) {
            // INSERT
            String cols = String.join(", ", fields.keySet());
            String params = fields
                    .keySet()
                    .stream()
                    .map(k -> ":" + k)
                    .collect(java.util.stream.Collectors.joining(", "));
            String sql =
                    "INSERT INTO public." + table + " (" + cols + ") VALUES (" + params + ")";
            jdbc.update(sql, new MapSqlParameterSource(fields));
        } else {
            // UPDATE
            String setPart = fields
                    .keySet()
                    .stream()
                    .map(k -> k + " = :" + k)
                    .collect(java.util.stream.Collectors.joining(", "));
            String sql = "UPDATE public." + table + " SET " + setPart + " WHERE id = :id";
            fields.put("id", id);
            jdbc.update(sql, new MapSqlParameterSource(fields));
        }
    }

    // удалить запись
    @DeleteMapping("/tables/{table}/{id}")
    public void deleteRow(@PathVariable String table, @PathVariable Long id) {
        validateTableName(table);
        String sql = "DELETE FROM public." + table + " WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        jdbc.update(sql, params);
    }

    // /api/reports/{report}
    @GetMapping("/reports/{report}")
    public List<Map<String, Object>> getReport(
            @PathVariable String report,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long saleId
    ) {
        String sql =
                switch (report) {
                    // 1. Продажи по месяцам
                    case "sales-monthly" -> {
                        if (year != null && month != null) {
                            // все продажи за конкретный месяц
                            yield """
                                SELECT s.id,
                                       s.sale_date,
                                       s.final_price,
                                       s.client_id,
                                       s.car_id
                                FROM sales s
                                WHERE EXTRACT(YEAR  FROM s.sale_date) = :year
                                  AND EXTRACT(MONTH FROM s.sale_date) = :month
                                ORDER BY s.sale_date
                                """;
                                            } else if (year != null) {
                                                // все продажи за год
                                                yield """
                                SELECT s.id,
                                       s.sale_date,
                                       s.final_price,
                                       s.client_id,
                                       s.car_id
                                FROM sales s
                                WHERE EXTRACT(YEAR FROM s.sale_date) = :year
                                ORDER BY s.sale_date
                                """;
                                            } else {
                                                // все продажи за всё время
                                                yield """
                                SELECT s.id,
                                       s.sale_date,
                                       s.final_price,
                                       s.client_id,
                                       s.car_id
                                FROM sales s
                                ORDER BY s.sale_date
                                """;
                        }
                    }

                    // 2. Топ клиентов
                    case "top-clients" -> {
                        if (limit != null && limit > 0) {
                            yield """
                                SELECT c.first_name || ' ' || c.last_name AS client,
                                       SUM(s.final_price)                AS total
                                FROM sales s
                                JOIN clients c ON s.client_id = c.id
                                GROUP BY c.id, client
                                ORDER BY total DESC
                                LIMIT :limit
                                """;
                        } else {
                            yield """
                                SELECT c.first_name || ' ' || c.last_name AS client,
                                       SUM(s.final_price)                AS total
                                FROM sales s
                                JOIN clients c ON s.client_id = c.id
                                GROUP BY c.id, client
                                ORDER BY total DESC
                                LIMIT 5
                                """;
                        }
                    }
                    // 3. Проданные машины
                    case "sold-cars" -> {
                        if (from != null && to != null) {
                            yield """
                                SELECT s.id,
                                       s.sale_date,
                                       s.final_price,
                                       b.name           AS brand,
                                       m.name           AS model,
                                       m.generation     AS generation,
                                       m.body_type      AS body_type,
                                       ca.vin_code      AS vin_code,
                                       ca.year_manufacture,
                                       ca.mileage,
                                       ca.price         AS car_price,
                                       ca.status        AS car_status
                                FROM sales s
                                JOIN cars   ca ON s.car_id = ca.id
                                JOIN models m  ON ca.model_id = m.id
                                JOIN brands b  ON m.brand_id = b.id
                                WHERE s.sale_date BETWEEN :from::timestamp AND :to::timestamp
                                ORDER BY s.sale_date
                                """;
                        } else {
                            yield """
                                SELECT s.id,
                                       s.sale_date,
                                       s.final_price,
                                       b.name           AS brand,
                                       m.name           AS model,
                                       m.generation     AS generation,
                                       m.body_type      AS body_type,
                                       ca.vin_code      AS vin_code,
                                       ca.year_manufacture,
                                       ca.mileage,
                                       ca.price         AS car_price,
                                       ca.status        AS car_status
                                FROM sales s
                                JOIN cars   ca ON s.car_id = ca.id
                                JOIN models m  ON ca.model_id = m.id
                                JOIN brands b  ON m.brand_id = b.id
                                ORDER BY s.sale_date
                                """;
                        }
                    }
                    // 4. Прибыль
                    case "profit" -> {
                        if (from != null && to != null) {
                            // Прибыль по дням в заданном диапазоне
                            yield """
                                SELECT
                                       date_trunc('day', sale_date) AS day,
                                       SUM(final_price)            AS total_profit
                                FROM sales
                                WHERE sale_date::date BETWEEN :from::date AND :to::date
                                GROUP BY day
                                ORDER BY day
                                """;
                        } else {
                            // Прибыль по месяцам за всё время
                            yield """
                                SELECT
                                       date_trunc('month', sale_date) + INTERVAL '1 month' AS month,
                                       SUM(final_price)               AS total_profit
                                FROM sales
                                GROUP BY month
                                ORDER BY month
                                """;
                        }
                    }

                    // 5. Сотрудники (поиск)
                    case "employees" -> {
                        if (q != null && !q.isBlank()) {
                            yield """
                                SELECT first_name,
                                       last_name,
                                       position,
                                       salary
                                FROM employees
                                WHERE first_name ILIKE '%' || :q || '%'
                                   OR last_name  ILIKE '%' || :q || '%'
                                ORDER BY last_name, first_name
                                """;
                        } else {
                            yield """
                                SELECT first_name,
                                       last_name,
                                       position,
                                       salary
                                FROM employees
                                ORDER BY last_name, first_name
                                """;
                        }
                    }
                    // 6. Услуги по продажам
                    case "services-sales" -> {
                        if (saleId != null) {
                            yield """
                                SELECT a.id,
                                       a.appointment_date,
                                       a.status,
                                       a.final_cost,
                                       s.id      AS sale_id,
                                       sv.name   AS service
                                FROM appointments a
                                JOIN sales    s  ON a.sale_id = s.id
                                JOIN services sv ON a.service_id = sv.id
                                WHERE s.id = :saleId
                                ORDER BY a.appointment_date
                                """;
                        } else {
                            yield """
                                SELECT a.id,
                                       a.appointment_date,
                                       a.status,
                                       a.final_cost,
                                       s.id      AS sale_id,
                                       sv.name   AS service
                                FROM appointments a
                                JOIN sales    s  ON a.sale_id = s.id
                                JOIN services sv ON a.service_id = sv.id
                                ORDER BY a.appointment_date
                                """;
                        }
                    }
                    default -> "SELECT 'Тест' AS test";
                };

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (from != null && to != null) {
            params.addValue("from", from);
            params.addValue("to", to);
        }
        if (year != null) {
            params.addValue("year", year);
        }
        if (month != null) {
            params.addValue("month", month);
        }
        if (limit != null && limit > 0) {
            params.addValue("limit", limit);
        }
        if (q != null && !q.isBlank()) {
            params.addValue("q", q);
        }
        if (saleId != null) {
            params.addValue("saleId", saleId);
        }

        return jdbc.queryForList(sql, params);
    }

    // /api/tables/{table}/by-fk — используется фронтом для one‑to‑many
    @GetMapping("/tables/{table}/by-fk")
    public List<Map<String, Object>> getByFk(
            @PathVariable String table,
            @RequestParam String column,
            @RequestParam String value
    ) {
        validateTableName(table);
        if (!column.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }

        String sql = "SELECT * FROM public." + table + " WHERE " + column + " = ?";
        Object typed;
        try {
            typed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            typed = value;
        }
        return jdbc.getJdbcTemplate().queryForList(sql, typed);
    }
}
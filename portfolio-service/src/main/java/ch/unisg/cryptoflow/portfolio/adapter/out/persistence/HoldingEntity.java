package ch.unisg.cryptoflow.portfolio.adapter.out.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "holding")
public class HoldingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 30, scale = 18)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal averagePurchasePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private PortfolioEntity portfolio;

    protected HoldingEntity() {}

    public HoldingEntity(String symbol, BigDecimal quantity,
                         BigDecimal averagePurchasePrice, PortfolioEntity portfolio) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
        this.portfolio = portfolio;
    }

    public Long getId() { return id; }

    public String getSymbol() { return symbol; }

    public BigDecimal getQuantity() { return quantity; }

    public BigDecimal getAveragePurchasePrice() { return averagePurchasePrice; }

    public PortfolioEntity getPortfolio() { return portfolio; }
}

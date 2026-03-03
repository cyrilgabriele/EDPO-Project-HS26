package ch.unisg.cryptoflow.portfolio.adapter.out.persistence;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolio")
public class PortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @OneToMany(mappedBy = "portfolio",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.EAGER)
    private List<HoldingEntity> holdings = new ArrayList<>();

    protected PortfolioEntity() {}

    public PortfolioEntity(String userId) {
        this.userId = userId;
    }

    public Long getId() { return id; }

    public String getUserId() { return userId; }

    public List<HoldingEntity> getHoldings() { return holdings; }
}

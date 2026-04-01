package ch.unisg.cryptoflow.portfolio.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "portfolio")
public class PortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Setter
    @Column(name = "user_name")
    private String userName;

    @OneToMany(mappedBy = "portfolio",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.EAGER)
    private List<HoldingEntity> holdings = new ArrayList<>();

    protected PortfolioEntity() {
    }

    public PortfolioEntity(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

}

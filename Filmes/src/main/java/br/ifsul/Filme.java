package br.ifsul;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
@Table(name = "FILME")
public class Filme {
	@Id
    @Column(name = "ID")
	private Long id;
    @Column(name = "TITULO")
	private String titulo;
    @Column(name = "DIRETOR")
	private String diretor;
    @Column(name = "ANO_LANCAMENTO")
	private String anoLancamento;
}

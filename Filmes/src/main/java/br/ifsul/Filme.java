package br.ifsul;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Filme {
	private Long id;
	private String titulo;
	private String diretor;
	private String anoLancamento;
}

package br.ifsul;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class FilmesController {
	public static void main(String[] args) {
		SpringApplication.run(FilmesController.class, args);
	}
	
	List<Filme> filmes = new ArrayList<Filme>();
	
	@GetMapping("/filmes")
	public ResponseEntity<List<Filme>> getAllFilmes() {
		return ResponseEntity.ok(filmes);
	}
	
	@GetMapping("/filmes/{id}")
	public ResponseEntity<?> getFilmeById(@PathVariable Long id) {
		for (Filme filme:filmes) {
			if (filme.getId() == id) {
				return ResponseEntity.ok(filme);
			}
		}
		return ResponseEntity.notFound().build();
	}
	
	@PostMapping("/filmes")
	public ResponseEntity<?> addFilme(@RequestBody Filme f) {
		if (!idExists(f.getId())){
			filmes.add(f);
			return ResponseEntity.ok(f);	
		}
		return ResponseEntity.status(400).body("ERRO: Id já cadastrado");

	}
	
	@PutMapping("/filmes/{id}")
	public ResponseEntity<?> editFilme(@PathVariable Long id, @RequestBody Filme f) {
		for (Filme filme:filmes) {
			if (filme.getId() == id) {
				filme.setId(f.getId());
				filme.setTitulo(f.getTitulo());
				filme.setDiretor(f.getDiretor());
				filme.setAnoLancamento(f.getAnoLancamento());

				return ResponseEntity.ok(filme);
			}
		}
		return ResponseEntity.status(404).body("ERRO: Id não encontrado");
	}
	
	@DeleteMapping("/filmes/{id}")
	public ResponseEntity<?> editFilme(@PathVariable Long id) {
		for (Filme filme:filmes) {
			if (filme.getId() == id) {
				filmes.remove(filme);

				return ResponseEntity.ok(filme);
			}
		}
		return ResponseEntity.status(404).body("ERRO: Id não encontrado");
	}
	
	private boolean idExists(Long id) {
		for (Filme filme:filmes) {
			if (filme.getId() == id) {
				return true;
			}
		}
		return false;
	}

}

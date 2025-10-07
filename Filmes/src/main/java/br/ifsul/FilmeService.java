package br.ifsul;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilmeService {

    private final FilmeRepository filmeRepository;

    public FilmeService(FilmeRepository filmeRepository) {
        this.filmeRepository = filmeRepository;
    }

    @Transactional(readOnly = true)
    public Page<Filme> listarFilmes(Pageable pageable) {
        return filmeRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Filme> buscarPorId(Long id) {
        return filmeRepository.findById(id);
    }

    @Transactional
    public Filme criar(Filme filme) {
        filme.setId(null);
        return filmeRepository.save(filme);
    }

    @Transactional
    public Optional<Filme> atualizar(Long id, Filme filmeAtualizado) {
        return filmeRepository.findById(id).map(filmeExistente -> {
            filmeExistente.setTitulo(filmeAtualizado.getTitulo());
            filmeExistente.setDiretor(filmeAtualizado.getDiretor());
            filmeExistente.setAnoLancamento(filmeAtualizado.getAnoLancamento());
            return filmeRepository.save(filmeExistente);
        });
    }

    @Transactional
    public boolean remover(Long id) {
        return filmeRepository.findById(id).map(filme -> {
            filmeRepository.delete(filme);
            return true;
        }).orElse(false);
    }
}

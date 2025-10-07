package br.ifsul;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FilmeDataInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilmeDataInitializer.class);
    private static final long TARGET_TOTAL = 1_000L;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
    private static final int MAX_API_CALLS = 1_000;
    private static final List<String> SEARCH_TERMS = List.of(
            "a", "e", "i", "o", "u",
            "man", "woman", "star", "love", "dark",
            "night", "day", "war", "world", "king",
            "girl", "boy", "dead", "life", "time");

    private final FilmeRepository filmeRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private int apiCalls = 0;
    private boolean limitLogEmitted = false;

    @Value("${omdb.api.url:https://www.omdbapi.com/}")
    private String omdbApiUrl;

    @Value("${omdb.api.key:}")
    private String omdbApiKey;

    public FilmeDataInitializer(FilmeRepository filmeRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.filmeRepository = filmeRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        long existentes = filmeRepository.count();
        long faltantes = TARGET_TOTAL - existentes;
        if (faltantes <= 0) {
            LOGGER.debug("Filmes já possuem {} registros. Nenhuma importação necessária.", existentes);
            return;
        }

        if (omdbApiKey == null || omdbApiKey.isBlank()) {
            LOGGER.warn("Chave da API OMDb não configurada. Importação automática ignorada.");
            return;
        }

        try {
            List<Filme> novosFilmes = importarFilmesOmdb(faltantes);
            if (novosFilmes.isEmpty()) {
                LOGGER.warn("Nenhum filme válido encontrado durante a importação OMDb.");
                return;
            }

            filmeRepository.saveAll(novosFilmes);
            LOGGER.info("Importados {} filmes da OMDb API. Total atual: {}", novosFilmes.size(),
                    filmeRepository.count());
            if (limitReached()) {
                LOGGER.warn("Limite de {} chamadas à OMDb atingido durante a importação.", MAX_API_CALLS);
            }
        } catch (RestClientException | IOException e) {
            LOGGER.error("Falha ao importar filmes da OMDb API: {}", e.getMessage(), e);
        }
    }

    private List<Filme> importarFilmesOmdb(long faltantes) throws IOException {
        List<Filme> acumulado = new ArrayList<>();
        Set<String> chavesUnicas = new HashSet<>();

        for (String termo : SEARCH_TERMS) {
            if (acumulado.size() >= faltantes) {
                break;
            }
            if (limitReached()) {
                break;
            }
            for (int pagina = 1; pagina <= 10 && acumulado.size() < faltantes; pagina++) {
                if (limitReached()) {
                    break;
                }
                Optional<OmdbSearchResponse> resposta = buscarPagina(termo, pagina);
                if (resposta.isEmpty()) {
                    break;
                }
                OmdbSearchResponse searchResponse = resposta.get();
                if (!searchResponse.isSuccessful() || searchResponse.search() == null) {
                    break;
                }

                for (OmdbSearchMovie movie : searchResponse.search()) {
                    if (acumulado.size() >= faltantes) {
                        break;
                    }
                    if (limitReached()) {
                        break;
                    }
                    if (movie == null || movie.title() == null || movie.title().isBlank()) {
                        continue;
                    }
                    String chave = gerarChave(movie.title(), movie.year());
                    if (!chavesUnicas.add(chave)) {
                        continue;
                    }
                    Filme filme = converterParaFilme(movie);
                    if (filme != null) {
                        acumulado.add(filme);
                    }
                }

                // OMDb devolve no máximo 10 itens por página. Se vier menos, chegou ao fim.
                if (searchResponse.search().size() < 10) {
                    break;
                }
            }
        }

        return acumulado.size() > faltantes
                ? new ArrayList<>(acumulado.subList(0, (int) faltantes))
                : acumulado;
    }

    private Optional<OmdbSearchResponse> buscarPagina(String termo, int pagina) {
        if (!tryConsumeApiCall()) {
            return Optional.empty();
        }
        String url = omdbApiUrl + "?s=" + encode(termo) + "&type=movie&page=" + pagina + "&apikey=" + encode(omdbApiKey);
        String payload = restTemplate.getForObject(url, String.class);
        if (payload == null || payload.isBlank()) {
            LOGGER.debug("Resposta vazia para termo '{}' página {}.", termo, pagina);
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, OmdbSearchResponse.class));
        } catch (IOException e) {
            LOGGER.warn("Falha ao processar resposta da OMDb para termo '{}' página {}: {}", termo, pagina,
                    e.getMessage());
            return Optional.empty();
        }
    }

    private Filme converterParaFilme(OmdbSearchMovie movie) {
        String titulo = movie.title().trim();
        if (titulo.isBlank()) {
            return null;
        }

        String ano = extrairAno(movie.year());
        if (ano == null) {
            ano = "Não informado";
        }

        String diretor = buscarDiretor(movie.imdbId()).orElse("Diretor desconhecido");
        return new Filme(null, titulo, diretor, ano);
    }

    private Optional<String> buscarDiretor(String imdbId) {
        if (imdbId == null || imdbId.isBlank()) {
            return Optional.empty();
        }
        if (!tryConsumeApiCall()) {
            return Optional.empty();
        }
        String url = omdbApiUrl + "?i=" + encode(imdbId) + "&apikey=" + encode(omdbApiKey);
        try {
            String payload = restTemplate.getForObject(url, String.class);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            OmdbDetailResponse detail = objectMapper.readValue(payload, OmdbDetailResponse.class);
            if (!detail.isSuccessful()) {
                return Optional.empty();
            }
            return Optional.ofNullable(detail.director())
                    .filter(value -> !value.isBlank())
                    .filter(value -> !"N/A".equalsIgnoreCase(value))
                    .map(String::trim);
        } catch (IOException | RestClientException e) {
            LOGGER.debug("Falha ao consultar detalhes para imdbId {}: {}", imdbId, e.getMessage());
            return Optional.empty();
        }
    }

    private String gerarChave(String titulo, String anoBruto) {
        String ano = Optional.ofNullable(extrairAno(anoBruto)).orElse("?");
        return titulo.trim().toLowerCase() + "|" + ano;
    }

    private String extrairAno(String valor) {
        if (valor == null) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(valor);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String encode(String valor) {
        return URLEncoder.encode(Optional.ofNullable(valor).orElse(""), StandardCharsets.UTF_8);
    }

    private synchronized boolean tryConsumeApiCall() {
        if (apiCalls >= MAX_API_CALLS) {
            if (!limitLogEmitted) {
                limitLogEmitted = true;
                LOGGER.warn("Limite de {} chamadas à OMDb foi atingido. Interrompendo novas requisições.", MAX_API_CALLS);
            }
            return false;
        }
        apiCalls++;
        return true;
    }

    private synchronized boolean limitReached() {
        return apiCalls >= MAX_API_CALLS;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OmdbSearchResponse(
            @JsonProperty("Search") List<OmdbSearchMovie> search,
            @JsonProperty("totalResults") String totalResults,
            @JsonProperty("Response") String response,
            @JsonProperty("Error") String error) {

        boolean isSuccessful() {
            return "True".equalsIgnoreCase(response);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OmdbSearchMovie(
            @JsonProperty("Title") String title,
            @JsonProperty("Year") String year,
            @JsonProperty("imdbID") String imdbId,
            @JsonProperty("Type") String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OmdbDetailResponse(
            @JsonProperty("Title") String title,
            @JsonProperty("Year") String year,
            @JsonProperty("Director") String director,
            @JsonProperty("Response") String response) {

        boolean isSuccessful() {
            return "True".equalsIgnoreCase(response);
        }
    }
}

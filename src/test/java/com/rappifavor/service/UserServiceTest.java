package com.rappifavor.service;

import com.rappifavor.model.RolUsuario;
import com.rappifavor.model.Usuario;
import com.rappifavor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserServiceTest — Tests unitarios para UserService.
 *
 * No requiere conexión a MongoDB ni Firebase.
 * Mockito simula el repositorio para aislar la lógica de negocio.
 *
 * Para correr solo estos tests:
 *   mvn test -Dtest=UserServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Tests unitarios")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    // ─── registrar() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Registro exitoso con email @iteso.mx válido")
    void registrar_emailValido_retornaUsuario() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        doNothing().when(userRepository).save(any());

        Usuario u = userService.registrar("uid-1", "Juan Pablo", "jp@iteso.mx", RolUsuario.USUARIO);

        assertEquals("uid-1",          u.getId());
        assertEquals("jp@iteso.mx",    u.getEmail());
        assertEquals(RolUsuario.USUARIO, u.getRol());
        assertTrue(u.isActivo());
        assertNotNull(u.getCreadoEn());
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("Registro con email @gmail.com → rechazado")
    void registrar_emailGmail_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.registrar("uid-2", "Test", "test@gmail.com", RolUsuario.USUARIO));

        assertTrue(ex.getMessage().contains("iteso.mx"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registro con email sin dominio → rechazado")
    void registrar_emailInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
            userService.registrar("uid-3", "Test", "sindominio", RolUsuario.USUARIO));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registro con email ya existente → rechazado")
    void registrar_emailDuplicado_lanzaExcepcion() {
        when(userRepository.findByEmail("dup@iteso.mx"))
            .thenReturn(Optional.of(new Usuario("uid-x", "X", "dup@iteso.mx", RolUsuario.USUARIO)));

        assertThrows(IllegalArgumentException.class, () ->
            userService.registrar("uid-nuevo", "Nuevo", "dup@iteso.mx", RolUsuario.USUARIO));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registro con Firebase UID ya existente → rechazado")
    void registrar_uidDuplicado_lanzaExcepcion() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findById("uid-dup"))
            .thenReturn(Optional.of(new Usuario("uid-dup", "Y", "y@iteso.mx", RolUsuario.USUARIO)));

        assertThrows(IllegalArgumentException.class, () ->
            userService.registrar("uid-dup", "Z", "z@iteso.mx", RolUsuario.USUARIO));

        verify(userRepository, never()).save(any());
    }

    // ─── desactivar() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Desactivar usuario existente → activo=false")
    void desactivar_usuarioExistente_desactivaCuenta() {
        Usuario u = new Usuario("uid-a", "Maria", "maria@iteso.mx", RolUsuario.USUARIO);
        when(userRepository.findById("uid-a")).thenReturn(Optional.of(u));
        when(userRepository.update(any())).thenReturn(true);

        userService.desactivar("uid-a");

        assertFalse(u.isActivo());
        verify(userRepository).update(u);
    }

    @Test
    @DisplayName("Desactivar usuario inexistente → IllegalArgumentException")
    void desactivar_usuarioInexistente_lanzaExcepcion() {
        when(userRepository.findById("uid-x")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.desactivar("uid-x"));
        verify(userRepository, never()).update(any());
    }

    // ─── activar() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Activar usuario desactivado → activo=true")
    void activar_usuarioDesactivado_activaCuenta() {
        Usuario u = new Usuario("uid-b", "Carlos", "carlos@iteso.mx", RolUsuario.USUARIO);
        u.setActivo(false);
        when(userRepository.findById("uid-b")).thenReturn(Optional.of(u));
        when(userRepository.update(any())).thenReturn(true);

        userService.activar("uid-b");

        assertTrue(u.isActivo());
        verify(userRepository).update(u);
    }

    // ─── cambiarRol() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cambiar rol USUARIO → REPARTIDOR")
    void cambiarRol_actualizaRolCorrectamente() {
        Usuario u = new Usuario("uid-c", "Sofia", "sofia@iteso.mx", RolUsuario.USUARIO);
        when(userRepository.findById("uid-c")).thenReturn(Optional.of(u));
        when(userRepository.update(any())).thenReturn(true);

        userService.cambiarRol("uid-c", RolUsuario.REPARTIDOR);

        assertEquals(RolUsuario.REPARTIDOR, u.getRol());
        verify(userRepository).update(u);
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById con usuario existente → Optional presente")
    void findById_usuarioExistente_retornaOptional() {
        Usuario u = new Usuario("uid-d", "Diego", "diego@iteso.mx", RolUsuario.USUARIO);
        when(userRepository.findById("uid-d")).thenReturn(Optional.of(u));

        Optional<Usuario> resultado = userService.findById("uid-d");

        assertTrue(resultado.isPresent());
        assertEquals("Diego", resultado.get().getNombre());
    }

    @Test
    @DisplayName("findById con usuario inexistente → Optional vacío")
    void findById_usuarioInexistente_retornaVacio() {
        when(userRepository.findById("uid-x")).thenReturn(Optional.empty());
        assertTrue(userService.findById("uid-x").isEmpty());
    }

    @Test
    @DisplayName("findAll retorna todos los usuarios")
    void findAll_retornaListaCompleta() {
        when(userRepository.findAll()).thenReturn(List.of(
            new Usuario("u1", "A", "a@iteso.mx", RolUsuario.USUARIO),
            new Usuario("u2", "B", "b@iteso.mx", RolUsuario.REPARTIDOR)
        ));

        assertEquals(2, userService.findAll().size());
    }

    @Test
    @DisplayName("findByRol retorna solo usuarios del rol indicado")
    void findByRol_filtraCorrectamente() {
        when(userRepository.findByRol(RolUsuario.REPARTIDOR)).thenReturn(List.of(
            new Usuario("r1", "Rep", "rep@iteso.mx", RolUsuario.REPARTIDOR)
        ));

        List<Usuario> resultado = userService.findByRol(RolUsuario.REPARTIDOR);

        assertEquals(1, resultado.size());
        assertEquals(RolUsuario.REPARTIDOR, resultado.get(0).getRol());
    }
}

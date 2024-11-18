import pygame
import socket
import threading

# Constantele pentru server și buffer
SERVER_HOST = '192.168.0.108'  # IP-ul serverului
SERVER_PORT = 12345  # Portul de conectare la server
BUFF_SIZE = 1024  # Dimensiunea bufferului pentru datele primite de la server

# Configurarea Pygame
pygame.init()  # Inițializarea Pygame
screen = pygame.display.set_mode((
    800, 600))  # Setează dimensiunea ferestrei jocului
pygame.display.set_caption('Click the Shape')  # Titlul ferestrei jocului
font = pygame.font.SysFont('Arial', 24)  # Fontul utilizat pentru textul scorului

# Variabilele jocului
current_shape = None  # Forma curentă de desenat (inițial nu este setată)
score = 0  # Scorul jocului
game_running = True  # Flag care indică dacă jocul este în desfășurare


def receive_data(client_socket):
    """
    Gestionează primirea datelor de la server.
    Se execută într-un fir separat și ascultă continuu pentru a actualiza starea jocului.
    """
    global current_shape, score, game_running

    while game_running:
        # Primește date de la server
        data = client_socket.recv(BUFF_SIZE).decode()

        # Verifică tipul mesajului primit
        if data.startswith("SHAPE"):
            # Despachetează informațiile despre formă: culoare (RGB), coordonate (x, y) și rază
            _, color_r, color_g, color_b, x, y, radius = data.split()
            current_shape = ((int(color_r), int(color_g), int(color_b)), int(x), int(y), int(radius))
        elif data.startswith("SCORE"):
            # Actualizează scorul primit de la server
            _, score_value = data.split()
            score = int(score_value)
        elif data.startswith("GAME_OVER"):
            # Dacă serverul trimite mesajul "GAME_OVER", înseamnă că jocul s-a încheiat
            print("Game over!")
            game_running = False


def draw_shape(shape):
    """
    Desenează forma curentă pe ecran.

    :param shape: Tuple care conține informațiile formei (culoare, x, y, rază)
    """
    if shape:
        pygame.draw.circle(screen, shape[0], (shape[1], shape[2]), shape[3])  # Desenează un cerc


def check_click(x, y, shape):
    """
    Verifică dacă jucătorul a dat click pe formă.

    :param x: Coordonata x a clicului
    :param y: Coordonata y a clicului
    :param shape: Forma pe care trebuie să o verificăm
    :return: True dacă clicul a fost în interiorul formei, False altfel
    """
    # Calculează distanța dintre punctul clicuit și centrul formei (cerc)
    dist = ((x - shape[1])**2 + (y - shape[2])**2)**0.5
    return dist <= shape[3]  # Dacă distanța este mai mică sau egală cu raza, considerăm că a fost un click valid


def main():
    """
    Loop-ul principal al jocului.
    Creează o conexiune la server și gestionează interacțiunile cu utilizatorul.
    """
    global current_shape, game_running

    # Creează un socket client pentru a se conecta la server
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((SERVER_HOST, SERVER_PORT))  # Conectează-te la server

    # Pornește un fir separat pentru a asculta datele de la server
    threading.Thread(target=receive_data, args=(client_socket,), daemon=True).start()

    while game_running:
        screen.fill((0, 0, 0))  # Curăță ecranul (background negru)

        # Desenează forma curentă pe ecran
        draw_shape(current_shape)

        # Afișează scorul curent în colțul stânga sus al ecranului
        score_text = font.render(f"Score: {score}", True, (255, 255, 255))
        screen.blit(score_text, (10, 10))

        # Gestionează evenimentele (clickuri, închidere fereastră)
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                pygame.quit()  # Închide Pygame
                game_running = False  # Oprește jocul
                return
            if event.type == pygame.MOUSEBUTTONDOWN:
                if current_shape:
                    x, y = pygame.mouse.get_pos()  # Obține coordonatele clickului
                    if check_click(x, y, current_shape):  # Verifică dacă clickul a fost pe formă
                        color = current_shape[0]
                        # Trimite mesaj serverului pentru a înregistra clickul
                        client_socket.send(f"CLICK {color[0]} {color[1]} {color[2]}\n".encode())
                        current_shape = None  # Șterge forma curentă după click

        pygame.display.flip()  # Actualizează ecranul

    # Închide conexiunea la server după terminarea jocului
    client_socket.close()

main()

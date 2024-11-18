import pygame
import socket
import threading

SERVER_HOST = '172.20.10.4'  # Replace with server IP
SERVER_PORT = 12345
BUFF_SIZE = 1024

# Pygame setup
pygame.init()
screen = pygame.display.set_mode((800, 600))
pygame.display.set_caption('Click the Shape')
font = pygame.font.SysFont('Arial', 24)

# Game variables
current_shape = None
score = 0
game_running = True


def receive_data(client_socket):
    """Handles receiving data from the server."""
    global current_shape, score, game_running

    while game_running:
        data = client_socket.recv(BUFF_SIZE).decode()
        print(data)
        if data.startswith("SHAPE"):
            _, color_r, color_g, color_b, x, y, radius = data.split()
            current_shape = ((int(color_r), int(color_g), int(color_b)), int(x), int(y), int(radius))
        elif data.startswith("SCORE"):
            _, score_value = data.split()
            score = int(score_value)
        elif data.startswith("GAME_OVER"):
            print("Game over!")
            game_running = False


def draw_shape(shape):
    """Draws the current shape on the screen."""
    if shape:
        pygame.draw.circle(screen, shape[0], (shape[1], shape[2]), shape[3])


def check_click(x, y, shape):
    """Checks if the player clicked on the shape."""
    dist = ((x - shape[1])**2 + (y - shape[2])**2)**0.5
    return dist <= shape[3]


def main():
    """Main game loop."""
    global current_shape, game_running

    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((SERVER_HOST, SERVER_PORT))

    threading.Thread(target=receive_data, args=(client_socket,), daemon=True).start()

    while game_running:
        screen.fill((0, 0, 0))  # Black background

        # Draw the current shape
        draw_shape(current_shape)

        # Display the score
        score_text = font.render(f"Score: {score}", True, (255, 255, 255))
        screen.blit(score_text, (10, 10))

        # Handle events
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                pygame.quit()
                game_running = False
                return
            if event.type == pygame.MOUSEBUTTONDOWN:
                if current_shape:
                    x, y = pygame.mouse.get_pos()
                    if check_click(x, y, current_shape):
                        color = current_shape[0]
                        client_socket.send(f"CLICK {color[0]} {color[1]} {color[2]}\n".encode())
                        current_shape = None  # Clear the shape after a click

        # print(game_running)
        pygame.display.flip()

    client_socket.close()


if __name__ == "__main__":
    main()

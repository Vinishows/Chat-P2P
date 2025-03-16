package br.com.peer;

import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.util.*;

public class Peer {
    private String username;
    private int port;
    private ServerSocket serverSocket;
    private List<Socket> connections = new ArrayList<>();
    public static boolean is_chatting = false;
    public static Peer hostPeer;

    public Peer(String username, int port) {
        this.username = username;
        this.port = port;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Peer " + username + " está ouvindo na porta " + port);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("7");
        }
    }

    public void start() {
        new Thread(this::listenForConnections).start();
        new Thread(this::listenForUserInput).start();
    }

    private void listenForConnections() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                connections.add(socket);
                new Thread(() -> handleConnection(socket)).start();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("6");
            }
        }
    }

    private void handleConnection(Socket socket) {
        if(!socket.isClosed()) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                //Thread.currentThread().interrupt();
                //e.printStackTrace();
                //System.out.println("4");
            }
        }
    }

    private void listenForUserInput() {
        try (BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                if (!is_chatting) {
                    System.out.println("Nenhuma conexão estabelecida");
                    NewConnection();
                } else {
                    String message = userInput.readLine();
                    broadcastMessage(message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("1");
        }
    }

    private void broadcastMessage(String message) {
        for (Socket socket : connections) {
            if (!socket.isClosed()) {
                try {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    if (message.contains("bye") && message.length() == 3) {
                        out.println(username + " Encerrou a conexão.");
                        System.out.println("Você encerrou a conexão.");
                        socket.close();
                        is_chatting = false;
                    } else {
                        out.println(username + ": " + message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("2");
                }
            }
        }
    }

    public void connectToPeer(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            connections.add(socket);
            new Thread(() -> handleConnection(socket)).start();
            System.out.println("Conectado ao peer em " + host + ":" + port);
        } catch (IOException e) {
            System.out.println("Erro ao conectar ao peer em " + host + ":" + port);
            e.printStackTrace();
            System.out.println("3");
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Solicita o nome do usuário
        System.out.print("Digite seu nome de usuário: ");
        String username = scanner.nextLine();

        // Solicita a porta
        System.out.print("Digite a porta para escutar: ");
        int port = scanner.nextInt();
        scanner.nextLine(); // Consumir a nova linha pendente

        // Inicia o peer
        hostPeer = new Peer(username, port);

        NewConnection();
    }

    public static void NewConnection() {
        //hostPeer.is_chatting = true;
        Scanner scanner = new Scanner(System.in);
        is_chatting = true;
        hostPeer.start();

        // Pergunta se deseja conectar a outro peer
        System.out.print("Deseja conectar a outro peer? (s/n): ");
        String resposta = scanner.nextLine();

        if (resposta.equalsIgnoreCase("s")) {
            System.out.print("Digite o endereço do peer (host): ");
            String peerHost = scanner.nextLine();

            System.out.print("Digite a porta do peer: ");
            int peerPort = scanner.nextInt();
            scanner.nextLine(); // Consumir a nova linha pendente

            // Conecta ao peer
            hostPeer.connectToPeer(peerHost, peerPort);
        }
    }
}
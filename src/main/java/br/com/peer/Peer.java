package br.com.peer;

import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {
    private String username;
    private int port;
    private ServerSocket serverSocket;
    private List<Socket> connections = new ArrayList<>();
    public static boolean is_chatting = false;
    public static Peer hostPeer;
    public static Map<String, List<String>> history = new HashMap<>();
    public static String hostAtual;
    public static boolean is_connecting = false;

    // Constantes para descoberta
    private static final int DISCOVERY_PORT = 8888;
    private static final String DISCOVERY_REQUEST = "PEER_DISCOVERY_REQUEST";
    private static final String DISCOVERY_RESPONSE = "PEER_DISCOVERY_RESPONSE";

    // Classe auxiliar para armazenar informações de peers
    public static class PeerInfo {
        public String username;
        public String ip;
        public int port;

        public PeerInfo(String username, String ip, int port) {
            this.username = username;
            this.ip = ip;
            this.port = port;
        }

        @Override
        public String toString() {
            return username + " - " + ip + ":" + port;
        }
    }

    public Peer(String username, int port) {
        this.username = username;
        try {
            // Se a porta for 0, o ServerSocket aloca automaticamente uma porta livre.
            serverSocket = new ServerSocket(port);
            // Atualiza o valor da porta para o que foi realmente alocado.
            this.port = serverSocket.getLocalPort();
            System.out.println("Peer " + username + " está ouvindo na porta " + this.port);
            System.out.println("Endereço IP: " + getLocalIPAddress());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Erro ao iniciar o servidor.");
        }
    }

    public void start() {
        new Thread(this::listenForConnections).start();
        new Thread(this::listenForUserInput).start();
    }

    // Inicia o serviço que responde às requisições de descoberta de peers
    public void startDiscoveryResponder() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT, InetAddress.getByName("0.0.0.0"))) {
                socket.setBroadcast(true);
                while (true) {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    if (message.equals(DISCOVERY_REQUEST)) {
                        // Monta a resposta com informações: username, IP e porta do peer
                        String response = DISCOVERY_RESPONSE + ":" + username + ":" + getLocalIPAddress() + ":" + port;
                        byte[] responseData = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(
                                responseData, responseData.length, packet.getAddress(), packet.getPort());
                        socket.send(responsePacket);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Envia uma mensagem de descoberta via UDP e aguarda respostas de outros peers
    public List<PeerInfo> discoverPeers() {
        List<PeerInfo> discoveredPeers = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            // Envia a requisição de descoberta para a rede
            byte[] requestData = DISCOVERY_REQUEST.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(
                    requestData, requestData.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
            socket.send(requestPacket);

            // Define timeout para coleta de respostas (3 segundos)
            socket.setSoTimeout(3000);
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 3000) {
                try {
                    byte[] buf = new byte[256];
                    DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
                    socket.receive(responsePacket);
                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    if (response.startsWith(DISCOVERY_RESPONSE)) {
                        // Formato da resposta: "PEER_DISCOVERY_RESPONSE:username:ip:port"
                        String[] parts = response.split(":");
                        if (parts.length == 4) {
                            String peerUsername = parts[1];
                            String peerIp = parts[2];
                            int peerPort = Integer.parseInt(parts[3]);
                            // Exclui a si mesmo da lista
                            if (!(peerIp.equals(getLocalIPAddress()) && peerPort == port)) {
                                PeerInfo peerInfo = new PeerInfo(peerUsername, peerIp, peerPort);
                                boolean exists = false;
                                for (PeerInfo pi : discoveredPeers) {
                                    if (pi.ip.equals(peerInfo.ip) && pi.port == peerInfo.port) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    discoveredPeers.add(peerInfo);
                                }
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break; // tempo esgotado para receber respostas
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return discoveredPeers;
    }

    // Método atualizado para obter um endereço IPv4 local não-loopback
    public String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                // Ignora interfaces que não estejam ativas ou sejam virtuais
                if (!netint.isUp() || netint.isLoopback() || netint.isVirtual()) continue;
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress addr : Collections.list(inetAddresses)) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        // Caso não encontre, retorna o localhost
        return "127.0.0.1";
    }

    private void listenForConnections() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                connections.add(socket);
                new Thread(() -> handleConnection(socket)).start();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Erro ao aceitar conexão.");
            }
        }
    }

    private void handleConnection(Socket socket) {
        if (!socket.isClosed()) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println(message);
                    if (history.containsKey(hostAtual) && is_chatting) {
                        history.get(hostAtual).add(message);
                    }
                }
            } catch (IOException e) {
                // Trata exceção silenciosamente para evitar interrupção de thread
            }
        }
    }

    private void listenForUserInput() {
        try (BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                if (!is_connecting) {
                    System.out.println("Nenhuma conexão estabelecida");
                    NewConnection();
                } else {
                    String message = userInput.readLine();
                    broadcastMessage(message);
                    if (history.containsKey(hostAtual) && is_chatting) {
                        history.get(hostAtual).add(message);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Erro ao ler entrada do usuário.");
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
                        is_connecting = false;
                    } else {
                        out.println(username + ": " + message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Erro ao enviar mensagem.");
                }
            }
        }
    }

    public void connectToPeer(String host, int port) {
        try {
            hostAtual = host;
            Socket socket = new Socket(host, port);
            connections.add(socket);
            new Thread(() -> handleConnection(socket)).start();
            System.out.println("Conectado ao peer em " + host + ":" + port);
            if (!history.containsKey(host)) {
                history.put(host, new ArrayList<>());
                System.out.println("Nova entrada criada para: " + host);
            } else {
                List<String> historico = history.get(host);
                for (String msg : historico) {
                    System.out.println(msg);
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao conectar ao peer em " + host + ":" + port);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println(history);
        Scanner scanner = new Scanner(System.in);

        // Solicita apenas o nome do usuário (IP e porta serão obtidos automaticamente)
        System.out.print("Digite seu nome de usuário: ");
        String username = scanner.nextLine();

        // Cria o peer com porta 0 para alocação automática
        hostPeer = new Peer(username, 0);

        // Inicia o responder de descoberta para que este peer possa ser encontrado por outros na mesma rede
        hostPeer.startDiscoveryResponder();

        // Inicia a conexão
        NewConnection();
    }

    public static void NewConnection() {
        Scanner scanner = new Scanner(System.in);
        is_connecting = true;
        hostPeer.start();

        // Pergunta se deseja conectar a outro peer
        System.out.print("Deseja conectar a outro peer? (s/n): ");
        String resposta = scanner.nextLine();

        if (resposta.equalsIgnoreCase("s")) {
            // Descobre os peers disponíveis via UDP
            List<PeerInfo> availablePeers = hostPeer.discoverPeers();
            if (availablePeers.isEmpty()) {
                System.out.println("Nenhum peer disponível no momento. Tente novamente mais tarde.");
            } else {
                System.out.println("Peers disponíveis:");
                for (int i = 0; i < availablePeers.size(); i++) {
                    System.out.println(i + ": " + availablePeers.get(i).toString());
                }
                System.out.print("Digite o número do peer para se conectar: ");
                int choice = scanner.nextInt();
                scanner.nextLine(); // Consumir a nova linha pendente
                if (choice >= 0 && choice < availablePeers.size()) {
                    PeerInfo selectedPeer = availablePeers.get(choice);
                    is_chatting = true;
                    hostPeer.connectToPeer(selectedPeer.ip, selectedPeer.port);
                } else {
                    System.out.println("Opção inválida.");
                }
            }
        }
        // Se o usuário responder "n", o peer ficará aguardando conexões de outros peers.
    }
}

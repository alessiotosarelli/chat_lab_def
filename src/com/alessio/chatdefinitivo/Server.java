package com.alessio.chatdefinitivo;

import java.io.*; // importo la libreria globale di "java.io"
import java.net.*; // importo la libreria globale di "java.net"
import java.util.concurrent.ConcurrentHashMap; // importo la libreria "java.util.concurrent.ConcurrentHashMap"

public class Server
{
    private static final int PORT = 12345;
    // !!!! ----> Questa è un'HashMap che ha come chiave il nickname e di valore ClientHandler che è un Runnable <---- !!!!
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        InetAddress addr; // creazione di un InetAddress in modo da poter connettere il server su un IP specifico
        try {
            addr = InetAddress.getByName("localhost"); // IP al quale connettere il server
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Server avviato su localhost, porta " + PORT);
        // vado a creare un'oggeto "serverSocket" assegnando la porta.
        try (ServerSocket serverSocket = new ServerSocket(PORT, 0, addr)) {

            while (true) {
                //Il server è in attesa della connessione del client
                Socket clientSocket = serverSocket.accept();
                // si crea un nuovo thread che gli passa un CLientHandler che è un Runnable.
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//ClientHandler è una classe di tipo Runnable
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private String nickname;
        private PrintWriter out;

        // Questo cotsruttore ha bisogno di una Socket
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run()
        {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                this.out = out;
                out.println("Inserisci il tuo nickname:");

                while (true) {
                    nickname = in.readLine();
                    if (nickname == null || nickname.isBlank() || clients.containsKey(nickname)) {
                        out.println("Nickname non valido o già in uso. Riprova:");
                    } else {
                        synchronized (clients) {
                            clients.put(nickname, this);
                            break;
                        }
                    }
                }
                out.println("Benvenuto, " + nickname + "!");
                broadcast(nickname + " si è connesso.", null);

                String message;
                while ((message = in.readLine()) != null)
                {
                    // Questo comando invia un messaggio ad un destinatario specifico
                    if (message.startsWith("DIRECT "))
                    {
                        handleDirectMessage(message);
                    }
                    // questo comando invia un messaggio a tutti i destinatari connessi
                    else if (message.startsWith("BROADCAST "))
                    {
                        handleBroadcastMessage(message);
                    }
                    // questo comando visualizza tutti i client connessi
                    else if (message.equals("LIST"))
                    {
                        handleListRequest();
                    }
                    // questo comando disconnette il client
                    else if (message.equals("QUIT"))
                    {
                        break;
                    }
                    // questo comando cambia il nickname
                    else if (message.startsWith("NICK"))
                    {
                        handleNickRequest(message);
                    }
                    else
                    {
                        out.println("Comando non riconosciuto.");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }

        private void handleDirectMessage(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                out.println("Formato comando non valido. Usa: DIRECT <destinatario> <messaggio>");
                return;
            }
            String recipient = parts[1];
            String text = parts[2];
            ClientHandler recipientHandler = clients.get(recipient);
            if (recipientHandler != null) {
                recipientHandler.out.println("[Privato da " + nickname + "]: " + text);
                out.println("Messaggio inviato a " + recipient);
            } else {
                out.println("Utente " + recipient + " non trovato.");
            }
        }

        private void handleBroadcastMessage(String message) {
            String text = message.substring(10);
            broadcast("[Broadcast da " + nickname + "]: " + text, this);
        }

        private void handleListRequest() {
            out.println("Utenti connessi: " + String.join(", ", clients.keySet()));
        }

        private void broadcast(String message, ClientHandler exclude) {
            for (ClientHandler client : clients.values()) {
                if (client != exclude) {
                    client.out.println(message);
                }
            }
        }

        private void handleNickRequest(String message)
        {
            String newNickname = message.split(" ")[1];
            out.println("Nickname cambiato in: " + newNickname);
            synchronized (clients) {
                clients.put(newNickname, this);
                clients.remove(this.nickname);
            }
            this.nickname = newNickname;
        }

        private void disconnect() {
            if (nickname != null) {
                clients.remove(nickname);
                broadcast(nickname + " si è disconnesso.", null);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}


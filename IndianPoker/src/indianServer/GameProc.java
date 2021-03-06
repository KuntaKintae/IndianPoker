package indianServer;
import java.awt.AWTException;
import java.awt.Robot;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class GameProc implements Runnable {
	private GameRoom procRoom;
	private GameUser p1, p2;
	private OutputStream out, out2;
	private DataOutputStream dout, dout2;
	private InputStream in, in2;
	private DataInputStream din, din2;
	private Socket sock, sock2;
	ServerSocket chattingSocket;
	private boolean whoami = false;// 방장구분

	GameProc(GameUser user) {
		procRoom = user.getRoom();
		if (user.equals(procRoom.getOwner())) {
			whoami = true;
			p1 = user;
			p2 = procRoom.getOther();
		} else {
			whoami = false;
			p1 = procRoom.getOwner();
			p2 = user;
		}
		procRoom.setStatus(false);
		this.run();
		RoomManager.deleteRoom(procRoom);
	}

	@Override
	public void run() {
		sock = p1.getSocket();
		sock2 = p2.getSocket();
		try {
			in = sock.getInputStream();
			in2 = sock2.getInputStream();
			out = sock.getOutputStream();
			out2 = sock2.getOutputStream();
			din = new DataInputStream(in);
			din2 = new DataInputStream(in2);
			dout = new DataOutputStream(out);
			dout2 = new DataOutputStream(out2);
			
			String info = whoami + procRoom.toString();
			System.out.println("게임이 시작되었습니다 " + info);
			if (!whoami) {
				dout2.writeUTF(info);
				
				boolean isEnd;
				int cPort = 9976;
				chattingSocket = new ServerSocket(cPort);
				Socket p1 = chattingSocket.accept();
				Socket p2 = chattingSocket.accept();
				DataOutputStream outp1 = new DataOutputStream(p1.getOutputStream());
				DataInputStream inp1 = new DataInputStream(p1.getInputStream());
				DataOutputStream outp2 = new DataOutputStream(p2.getOutputStream());
				DataInputStream inp2 = new DataInputStream(p2.getInputStream());
				
				do {
					try {
						outp1.writeUTF("msgStart");
						outp2.writeUTF("msgStart");
						
						String p1msg = inp1.readUTF();
						String p2msg = inp2.readUTF();
						
						if(!p1msg.equals("null"))
							outp2.writeUTF(p1msg);
						if(!p2msg.equals("null"))
							outp1.writeUTF(p2msg);
						
					} catch (IOException e) {					
						e.printStackTrace();
					}
					
					isEnd = procRoom.getSatus();
				} while (!isEnd);
				
				outp1.writeUTF("END");
				outp2.writeUTF("END");
			} else {
				
				dout.writeUTF(info);
				
				// 선결정
				procRoom.initDeck();
				do {
					p1.setMyCard(procRoom.drawDeck());
					p2.setMyCard(procRoom.drawDeck());
				} while (p1.getMyCard() == p2.getMyCard());

				procRoom.whoFirst();
				dout.flush();
				dout.writeInt(p1.getMyCard());
				dout.writeInt(p2.getMyCard());
				dout2.writeInt(p1.getMyCard());
				dout2.writeInt(p2.getMyCard());

				System.out.println("first dicision P1: " + p1.getMyCard() + " P2 :"
						+ p2.getMyCard());
				// 게임

				int gamecount = 0;
				while (p1.getGarnet() > 0 && p2.getGarnet() > 0) {
					gamecount++;
					p1.setDie(false); p2.setDie(false);
					dout.writeUTF("continue");
					dout2.writeUTF("continue");
					
					dout.writeInt(p2.getGarnet());
					dout2.writeInt(p1.getGarnet());
					
					dout.writeInt(procRoom.getBetSum());
					dout.writeInt(procRoom.getBetting());
					dout2.writeInt(procRoom.getBetSum());
					dout2.writeInt(procRoom.getBetting());
					
					if (gamecount == 10)
						procRoom.initDeck(); // 10회마다 덱을 변경해야한다.
					p1.setMyCard(procRoom.drawDeck()); // 덱에서 카드를 뽑고
					p2.setMyCard(procRoom.drawDeck());
					System.out.println("p1 : " + procRoom.getOwner().getMyCard() + " p2 : " + procRoom.getOther().getMyCard());
					System.out.println("pl garnet = " + p1.getGarnet() + ", p2 garnet = " + p2.getGarnet());
					dout.flush();
					dout.writeInt(p2.getMyCard()); // 상대의 값만 전송함
					dout2.writeInt(p1.getMyCard());

					String answer;//배팅
					do {
						if (p1.getMyTurn() == true) {//1p 턴
							dout.writeUTF("Your turn");
							dout2.writeUTF("Wait turn");
							answer = bettingProc(p1, dout, din);
							System.out.println("배팅받음");
							showBetting(p1, dout2, din2, answer);
						} else {
							dout2.writeUTF("Your turn");
							dout.writeUTF("Wait turn");
							answer = bettingProc(p2, dout2, din2);
							System.out.println("배팅받음");
							showBetting(p2, dout, din, answer);
						}

					} while (answer.equals("betting"));
					dout.writeUTF("Betting end");
					dout2.writeUTF("Betting end");

					// 결과확인
					dout.writeInt(p1.getMyCard());// 내카드 정보를 주고
					dout2.writeInt(p2.getMyCard());
					
					if(p1.getDie() == false && p2.getDie() == false && p1.getMyCard() == p2.getMyCard()) {
						
						dout.writeInt(p1.getGarnet());
						dout2.writeInt(p2.getGarnet());	
						continue;
					}
					
					if (p1.getDie() == true && p1.getMyCard() == 10) {//die했는데 10인경우
						p1.decGarnet(5);
						procRoom.setBetting(5);
						p2.setMyTurn(true);
						p1.setMyTurn(false);
					}else if(p2.getDie() == true && p2.getMyCard() == 10) {
						p2.decGarnet(5);
						procRoom.setBetting(5);
						p1.setMyTurn(true);
						p2.setMyTurn(false);
					}else if (p1.getMyCard() > p2.getMyCard()) {// p1 이겼을 경우  콜-카드가 높은경우 && 다이안했을 경우
						p1.incGarnet(procRoom.getBetSum()); // 배팅금액을 모두 내가 가짐
						p1.setMyTurn(true);
						p2.setMyTurn(false);
					}else { //졌을 경우 가넷은 배팅하면서 뺏기때문에 뺄 필요는 없다.
						p2.incGarnet(procRoom.getBetSum());
						p2.setMyTurn(true);
						p1.setMyTurn(false);
					}
					
					procRoom.initBetting();// 배팅 초기화
					dout.writeInt(p1.getGarnet());
					dout2.writeInt(p2.getGarnet());
						
				}
							
				procRoom.setStatus(true);
				dout.flush();
				dout2.flush();
				dout.writeUTF("Game Over");
				dout2.writeUTF("Game Over");
			}
			// 게임종료 승자처리 승패 저장 대기실로 내쫒음
			procRoom.close();
			chattingSocket.close();
			
		} catch (IOException e) {
			p1.detachRoom(procRoom);
			e.printStackTrace();
		}
	}
	
	private void showBetting(GameUser player, DataOutputStream send, DataInputStream recv, String op) {
		try {
			send.writeUTF(op);
			send.writeInt(procRoom.getBetting());
			send.writeInt(player.getGarnet());
			send.writeInt(procRoom.getBetSum());
			System.out.println("배팅됨 : " + procRoom.getBetting() + " 상대 남은 가넷" + player.getGarnet() + " 총 배팅액 " + procRoom.getBetSum());
		}catch(IOException e) {} 		
	}

	private String bettingProc(GameUser player, DataOutputStream send, DataInputStream recv) {
		String answer = null;
		try {
			answer = recv.readUTF();
			System.out.println("get op : " + answer);
			if (answer.equals("betting")) {
				send.writeInt(procRoom.getBetting());
				if (procRoom.getBetting() == 0) {// 첫턴인 경우
					inputBetting(player, send, recv);
				} else {// 상대의 배팅을 이어받는 경우
					int nowBetting = procRoom.getBetting();	
					nowBetting += recv.readInt();
					inputBetting(player, nowBetting);
				}
			} else if (answer.equals("call")) {
				int currentBetting = procRoom.getBetting();
				if (currentBetting > p1.getGarnet()) { // 콜했는데 배팅액보다 남은게 작은경우 올인처리
					inputBetting(player, player.getGarnet());
				} else {
					inputBetting(player, currentBetting);// 상대가 배팅한만큼 콜
			
				}
				send.writeInt(procRoom.getBetting());
				send.writeInt(procRoom.getBetSum());
				send.writeInt(player.getGarnet());
			}else if (answer.equals("die")) {
				player.setDie(true);
			}
		} catch (IOException e) {
		}
		return answer;
	}

	void inputBetting(GameUser player, DataOutputStream send, DataInputStream recv) {
		int betting;
		try {
			betting = recv.readInt(); // 배팅값 입력받음
			System.out.println("배팅금액 : " + betting);
			player.decGarnet(betting); // 배팅한 값만큼 가넷 감소
			procRoom.setBetting(betting);// 배팅값 입력
			procRoom.changeTurn(); // 턴을 넘김
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void inputBetting(GameUser player, int n) {
		int betting;

		betting = n;
		player.decGarnet(betting); // 배팅한 값만큼 가넷 감소
		procRoom.setBetting(betting);// 배팅값 입력
		procRoom.changeTurn(); // 턴을 넘김
	}
}

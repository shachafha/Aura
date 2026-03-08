"""
Aura WebSocket Test Client

Interactive test client for the Gemini Live API bidi-streaming endpoint.
Connects to ws://localhost:8080/ws/{session_id} and lets you send text
messages to the Aura stylist in real-time.

Usage:
    python test_ws_client.py
    python test_ws_client.py --session my-test-session
    python test_ws_client.py --host ws://your-server:8080
"""

import argparse
import asyncio
import json
import uuid

try:
    import websockets
except ImportError:
    print("Install websockets: pip install websockets")
    exit(1)


async def main(host: str, session_id: str):
    uri = f"{host}/ws/{session_id}"
    print(f"🔗 Connecting to {uri}...")
    print("─" * 60)

    async with websockets.connect(uri) as ws:
        print("✅ Connected! Type messages to chat with Aura.")
        print("   Type 'quit' to disconnect.\n")

        async def receive_events():
            """Listen for and display events from the server."""
            try:
                async for message in ws:
                    try:
                        event = json.loads(message)

                        # Display text content
                        content = event.get("content", {})
                        parts = content.get("parts", [])
                        for part in parts:
                            if "text" in part and part["text"]:
                                print(f"\n🤖 Aura: {part['text']}")

                        # Display transcriptions
                        input_t = event.get("input_audio_transcription", {})
                        if input_t and input_t.get("final_transcript"):
                            print(f"\n🎤 You said: {input_t['final_transcript']}")

                        output_t = event.get("output_audio_transcription", {})
                        if output_t and output_t.get("final_transcript"):
                            print(f"\n🔊 Aura said: {output_t['final_transcript']}")

                        # Display tool calls
                        tool_call = event.get("tool_call")
                        if tool_call:
                            print(f"\n🔧 Tool: {json.dumps(tool_call, indent=2)}")

                    except json.JSONDecodeError:
                        pass  # Skip non-JSON messages
            except websockets.ConnectionClosed:
                print("\n📡 Connection closed by server.")

        async def send_messages():
            """Read user input and send as text messages."""
            loop = asyncio.get_event_loop()
            while True:
                try:
                    user_input = await loop.run_in_executor(
                        None, lambda: input("\n💬 You: ")
                    )

                    if user_input.lower() in ("quit", "exit", "q"):
                        print("👋 Disconnecting...")
                        await ws.close()
                        break

                    if not user_input.strip():
                        continue

                    message = json.dumps({
                        "type": "text",
                        "text": user_input,
                    })
                    await ws.send(message)

                except EOFError:
                    break

        # Run both tasks concurrently
        await asyncio.gather(
            receive_events(),
            send_messages(),
            return_exceptions=True,
        )

    print("\n✅ Disconnected.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Aura WebSocket Test Client")
    parser.add_argument(
        "--host",
        default="ws://localhost:8080",
        help="WebSocket host URL (default: ws://localhost:8080)",
    )
    parser.add_argument(
        "--session",
        default=f"test-{uuid.uuid4().hex[:6]}",
        help="Session ID (default: random)",
    )
    args = parser.parse_args()

    print("🌟 Aura WebSocket Test Client")
    print(f"   Session: {args.session}")
    print()

    asyncio.run(main(args.host, args.session))

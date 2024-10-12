#!/usr/bin/env python
# pylint: disable=unused-argument
# This program is dedicated to the public domain under the CC0 license.

import json
import logging
import os
import re
import urllib

import aiohttp
from telegram import ForceReply, Update
from telegram.ext import Application, CommandHandler, ContextTypes, MessageHandler, filters

# Enable logging
logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s", level=logging.INFO
)
logging.getLogger("httpx").setLevel(logging.WARNING)

logger = logging.getLogger(__name__)

journals_list = None


async def start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    user = update.effective_user
    await update.message.reply_html(
        rf"Hi {user.mention_html()}!",
        reply_markup=ForceReply(selective=True),
    )
    await help_command(update, context)


async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    await update.message.reply_text("write:\n" +
                                    "\njournal [year] keywords - to search\n" +
                                    "\nYou can enter substring of a journal name" +
                                    "\nExamples:" +
                                    "\ntourism Венеция" +
                                    "\ndetishki 2017 риталин огнемёт" +
                                    "\n\nYou can also type\n" +
                                    "/journals - to get list of available for search journals"
                                    )


async def get_journals_list():
    async with aiohttp.ClientSession() as session:
        async with session.get('https://getlj.com/journals') as response:
            if response.status == 200:
                text = await response.text()
                data = json.loads(text)
                if isinstance(data, list):
                    journal_list = [item['journal'] for item in data if 'journal' in item]
                    return journal_list
                else:
                    return []
            else:
                return []


async def journals(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    global journals_list
    if not journals_list:
        journals_list = await get_journals_list()
    journal_text = "\n".join(journals_list)
    await update.message.reply_text(f"Available journals:\n{journal_text}")
    await help_command(update, context)


async def search(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    global journals_list
    if not journals_list:
        journals_list = await get_journals_list()
    text = update.message.text
    parts = text.split(" ", 2)

    journal = parts[0]
    year = ""
    words = ""

    if len(parts) >= 3 and re.match(r'^\d{4}$', parts[1]):
        # If the second part is a 4-digit number, treat it as a year
        year = parts[1]
        words = parts[2]
    elif len(parts) >= 2:
        # If there's no year, or it's not the second word, treat all remaining text as words
        words = ' '.join(parts[1:])

    journal_to_search = next((item for item in journals_list if journal in item), journal)

    # Construct the URL
    base_url = "https://getlj.com/search"
    query_params = {
        'journal': journal_to_search,
        'term': words,
        'year': year,
        'type': "Post"
    }
    # Remove None values
    query_params = {k: v for k, v in query_params.items() if v is not None}
    url = f"{base_url}?{urllib.parse.urlencode(query_params)}"

    async with aiohttp.ClientSession() as session:
        async with session.get(url) as response:
            if response.status == 200:
                # try:
                text = await response.text()
                data = json.loads(text)
                if not data:
                    await update.message.reply_text("No results found.")
                    return

                result_messages = []
                size = 0
                for item in data:
                    text = item.get('text', 'No text available').replace("<mark>", "").replace("</mark>", "")
                    url = item.get('url', '#')
                    text = f"<a href='{url}'>{text[:100]}...</a>"
                    result_messages.append(text)
                    size += len(text)
                    if size > 3500:
                        break

                # Join results, limiting to 4096 characters (Telegram message limit)

                result_text = "\n\n".join(result_messages)

                await update.message.reply_html(
                    f"Search results:\n\n{result_text}",
                    disable_web_page_preview=True
                )
            #  except json.JSONDecodeError:
            #      await update.message.reply_text("Failed to parse the search results.")
            else:
                await update.message.reply_text("Failed to retrieve search results.")

            await help_command(update, context)


def main() -> None:
    token = os.getenv('TELEGRAM_TOKEN')
    if not token:
        raise ValueError("No TELEGRAM_TOKEN found in environment variables.")

    application = Application.builder().token(token).build()

    application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, search))
    application.add_handler(CommandHandler("journals", journals))
    application.add_handler(CommandHandler("help", help_command))

    application.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()

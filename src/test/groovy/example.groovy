import java.util.regex.Pattern

def str = "http://rabota-il.livejournal.com/9069326.html?thread=70398990#t70398990"
def pattern = Pattern.compile('.*([0-9]+)$')

println pattern.matcher(str).find()

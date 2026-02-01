export class TeamcityMessage {
    private attributes: {key: string, value: string}[] = []
    constructor(private name: string) {

    }

    public static new(name: string): TeamcityMessage {
        return new TeamcityMessage(name);
    }

    addAttribute(key: string, value: string): TeamcityMessage {
        this.attributes.push({key, value});
        return this;
    }

    toString(): string {
        const attrs = this.attributes.map(attr => `${attr.key}='${escape(attr.value)}'`).join(' ');
        return `##teamcity[${this.name} ${attrs} timestamp='${getCurrentDate()}']`;
    }
}

function escape(text:string) {
    if (text == null) return undefined
    return text
        .replace(/\|/g, '||')
        .replace(/'/g, '|\'')
        .replace(/\n/g, '|n')
        .replace(/\r/g, '|r')
        .replace(/\[/g, '|[')
        .replace(/\]/g, '|]');
}
function getCurrentDate() {
    const date = new Date();
    const year = adjustToLength(date.getFullYear(), 4);
    const month = adjustToLength(date.getMonth(), 2);
    const day = adjustToLength(date.getDay(), 2);

    const hours = adjustToLength(date.getHours(), 2);
    const minutes = adjustToLength(date.getMinutes(), 2);
    const seconds = adjustToLength(date.getSeconds(), 2);
    const milliseconds = adjustToLength(date.getMilliseconds(), 3);

    const timezoneNum = Math.abs(date.getTimezoneOffset() / 60 * (-1));
    let timezone = adjustToLength(timezoneNum, 2);
    if (date.getTimezoneOffset() > 0) {
        timezone = '-' + timezone;
    } else {
        timezone = '+' + timezone;
    }

    return '' + year + '-' + month + '-' + day + "T" + hours + ':' + minutes + ':' + seconds + '.' + milliseconds + '' + timezone + '00';
}

function adjustToLength(number: number, length: number) {
    var result = '' + number.toString();
    while (result.length < length) {
        result = '0' + result;
    }
    return result;
}
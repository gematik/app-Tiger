export default class BannerMessage {
  text: string | null;
  color: string |null;

  constructor() {
    this.text = "";
    this.color = "";
  }

  public static fromJson(json: {  bannerMessage:string, bannerColor:string } ) : BannerMessage {
    const msg:BannerMessage = new BannerMessage();
    msg.text = json.bannerMessage;
    msg.color = json.bannerColor;
    return msg;
  }
}

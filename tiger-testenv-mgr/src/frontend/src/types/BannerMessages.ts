export default class BannerMessages {
  text: string | null;
  color: string |null;

  constructor() {
    this.text = "";
    this.color = "";
  }

  public static fromJson(json: {  bannerMessage:string, bannerColor:string } ) : BannerMessages {
    const msg:BannerMessages = new BannerMessages();
    msg.text = json.bannerMessage;
    msg.color = json.bannerColor;
    return msg;
  }
}

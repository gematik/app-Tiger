import BannerType from "./BannerType";

export default class BannerMessage {
  text: string | null;
  color: string |null;
  type: BannerType;
  isHtml: boolean;

  constructor() {
    this.text = "";
    this.color = "";
    this.type = BannerType.MESSAGE;
    this.isHtml = false;
  }

  public static fromJson(json: {  bannerMessage:string, bannerColor:string, bannerType: string, bannerIsHtml: boolean } ) : BannerMessage {
    const msg:BannerMessage = new BannerMessage();
    msg.text = json.bannerMessage;
    msg.color = json.bannerColor;
    msg.isHtml = json.bannerIsHtml;
    if (json.bannerType) {
      msg.type = json.bannerType as BannerType;
    }
    return msg;
  }
}
